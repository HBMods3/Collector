package com.example.data.github

import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class RemoteFileEntry(
    val path: String,
    val content: String,
    val sha: String
)

data class RemoteDirEntry(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val isDirectory: Boolean
)

data class RemoteCommitEntry(
    val sha: String,
    val message: String,
    val authorName: String,
    val dateIso: String
)

sealed class GitHubException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TokenExpiredException(msg: String = "GitHub session expired or revoked. Please reconnect your GitHub account.") : GitHubException(msg)
    class RateLimitException(msg: String) : GitHubException(msg)
    class ConflictException(msg: String) : GitHubException(msg)
    class ValidationException(msg: String) : GitHubException(msg)
    class NotFoundException(msg: String) : GitHubException(msg)
    class NetworkOrApiException(msg: String, cause: Throwable? = null) : GitHubException(msg, cause)
}

/**
 * Abstraction for GitHub repository JSON & file storage.
 * Enables both live GitHub REST API persistence and deterministic Robolectric testing.
 */
interface GitHubStoragePort {
    suspend fun getFile(token: String, owner: String, repo: String, path: String, ref: String? = null): RemoteFileEntry?
    suspend fun listDirectory(token: String, owner: String, repo: String, path: String): List<RemoteDirEntry>
    suspend fun putFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        expectedSha: String? = null,
        validateJson: Boolean = true
    ): String // Returns commit SHA
    suspend fun <T> updateJsonFileWithRetry(
        token: String,
        owner: String,
        repo: String,
        path: String,
        commitMessage: String,
        transform: (currentContent: String?) -> Pair<String, T>
    ): Pair<String, T> // Returns (commitSha, result)
    suspend fun deleteFile(token: String, owner: String, repo: String, path: String, sha: String, commitMessage: String)
    suspend fun listCommits(token: String, owner: String, repo: String, path: String?): List<RemoteCommitEntry>
    suspend fun <T> withRepoLock(owner: String, repo: String, block: suspend () -> T): T
}

class GitHubDatabaseClient(
    private val api: GitHubApiService
) : GitHubStoragePort {

    private val repoLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(owner: String, repo: String): Mutex {
        return repoLocks.getOrPut("$owner/$repo") { Mutex() }
    }

    override suspend fun <T> withRepoLock(owner: String, repo: String, block: suspend () -> T): T {
        return lockFor(owner, repo).withLock { block() }
    }

    private fun bearer(token: String): String = "Bearer $token"

    private fun validateJsonIfNeeded(path: String, content: String, validateJson: Boolean) {
        if (!validateJson || !path.endsWith(".json", ignoreCase = true)) return
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            throw GitHubException.ValidationException("JSON file $path cannot be empty.")
        }
        try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                throw GitHubException.ValidationException("Invalid JSON root in $path")
            }
        } catch (e: Exception) {
            throw GitHubException.ValidationException("Invalid JSON syntax for $path: ${e.message}")
        }
    }

    private fun <T> checkResponseError(response: Response<T>, pathOrContext: String): Nothing {
        val code = response.code()
        val errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
        when (code) {
            401 -> throw GitHubException.TokenExpiredException()
            403, 429 -> {
                val remaining = response.headers()["X-RateLimit-Remaining"]
                if (remaining == "0" || code == 429) {
                    throw GitHubException.RateLimitException("GitHub API rate limit reached. Please wait a minute and retry.")
                }
                throw GitHubException.NetworkOrApiException("GitHub permission denied (403) on $pathOrContext: ${errBody ?: ""}")
            }
            404 -> throw GitHubException.NotFoundException("Not found on GitHub: $pathOrContext")
            409, 422 -> throw GitHubException.ConflictException("GitHub SHA conflict ($code) on $pathOrContext")
            else -> throw GitHubException.NetworkOrApiException("GitHub error $code on $pathOrContext: ${errBody ?: response.message()}")
        }
    }

    override suspend fun getFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        ref: String?
    ): RemoteFileEntry? {
        return try {
            val resp = api.getFileContent(bearer(token), owner, repo, path, ref)
            if (resp.code() == 404) return null
            if (!resp.isSuccessful) checkResponseError(resp, path)
            val dto = resp.body() ?: return null
            val rawBase64 = dto.content?.replace("\n", "")?.replace("\r", "") ?: ""
            val decoded = String(Base64.decode(rawBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
            RemoteFileEntry(path = dto.path, content = decoded, sha = dto.sha)
        } catch (e: GitHubException) {
            throw e
        } catch (e: IOException) {
            throw GitHubException.NetworkOrApiException("Network error reading $path: ${e.message}", e)
        }
    }

    override suspend fun listDirectory(
        token: String,
        owner: String,
        repo: String,
        path: String
    ): List<RemoteDirEntry> {
        return try {
            val cleanPath = path.trimStart('/')
            val resp = api.listDirectory(bearer(token), owner, repo, cleanPath)
            if (resp.code() == 404) return emptyList()
            if (!resp.isSuccessful) checkResponseError(resp, cleanPath)
            val list = resp.body() ?: emptyList()
            list.map {
                RemoteDirEntry(
                    name = it.name,
                    path = it.path,
                    sha = it.sha,
                    size = it.size,
                    isDirectory = it.type == "dir"
                )
            }.sortedWith(compareByDescending<RemoteDirEntry> { it.isDirectory }.thenBy { it.name })
        } catch (e: GitHubException) {
            throw e
        } catch (e: IOException) {
            throw GitHubException.NetworkOrApiException("Network error listing $path: ${e.message}", e)
        }
    }

    override suspend fun putFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        expectedSha: String?,
        validateJson: Boolean
    ): String {
        validateJsonIfNeeded(path, content, validateJson)
        val encoded = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        var attempts = 0
        var lastError: Throwable? = null
        while (attempts < 3) {
            attempts++
            try {
                val resp = api.putFileContent(
                    authHeader = bearer(token),
                    owner = owner,
                    repo = repo,
                    path = path,
                    body = PutFileRequest(
                        message = commitMessage,
                        contentBase64 = encoded,
                        sha = expectedSha
                    )
                )
                if (resp.isSuccessful) {
                    return resp.body()?.commit?.sha ?: resp.body()?.content?.sha ?: ""
                }
                if (resp.code() == 409) {
                    throw GitHubException.ConflictException("SHA mismatch writing $path")
                }
                if (resp.code() in listOf(500, 502, 503, 504) && attempts < 3) {
                    delay(400L * attempts)
                    continue
                }
                checkResponseError(resp, path)
            } catch (e: IOException) {
                lastError = e
                if (attempts < 3) delay(400L * attempts)
            }
        }
        throw GitHubException.NetworkOrApiException("Failed to write $path after retries: ${lastError?.message}", lastError)
    }

    override suspend fun <T> updateJsonFileWithRetry(
        token: String,
        owner: String,
        repo: String,
        path: String,
        commitMessage: String,
        transform: (currentContent: String?) -> Pair<String, T>
    ): Pair<String, T> {
        var attempt = 0
        while (attempt < 4) {
            attempt++
            val existing = getFile(token, owner, repo, path)
            val (newContent, result) = transform(existing?.content)
            try {
                val commitSha = putFile(
                    token = token,
                    owner = owner,
                    repo = repo,
                    path = path,
                    content = newContent,
                    commitMessage = commitMessage,
                    expectedSha = existing?.sha,
                    validateJson = true
                )
                return commitSha to result
            } catch (_: GitHubException.ConflictException) {
                if (attempt >= 4) {
                    throw GitHubException.ConflictException("Could not resolve concurrent GitHub update conflict on $path.")
                }
                delay(250L * attempt)
            }
        }
        throw GitHubException.ConflictException("Concurrent update conflict on $path")
    }

    override suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        commitMessage: String
    ) {
        try {
            val resp = api.deleteFile(
                authHeader = bearer(token),
                owner = owner,
                repo = repo,
                path = path,
                body = DeleteFileRequest(message = commitMessage, sha = sha)
            )
            if (!resp.isSuccessful) checkResponseError(resp, path)
        } catch (e: GitHubException) {
            throw e
        } catch (e: IOException) {
            throw GitHubException.NetworkOrApiException("Network error deleting $path: ${e.message}", e)
        }
    }

    override suspend fun listCommits(
        token: String,
        owner: String,
        repo: String,
        path: String?
    ): List<RemoteCommitEntry> {
        return try {
            val resp = api.listCommitsForPath(bearer(token), owner, repo, path)
            if (!resp.isSuccessful) checkResponseError(resp, path ?: "commits")
            (resp.body() ?: emptyList()).map {
                RemoteCommitEntry(
                    sha = it.sha,
                    message = it.commit.message ?: "",
                    authorName = it.commit.author?.name ?: "GitHub",
                    dateIso = it.commit.author?.date ?: ""
                )
            }
        } catch (e: GitHubException) {
            throw e
        } catch (e: IOException) {
            throw GitHubException.NetworkOrApiException("Network error fetching commits: ${e.message}", e)
        }
    }
}
