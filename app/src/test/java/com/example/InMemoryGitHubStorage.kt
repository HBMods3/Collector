package com.example

import com.example.data.github.GitHubException
import com.example.data.github.GitHubStoragePort
import com.example.data.github.RemoteCommitEntry
import com.example.data.github.RemoteDirEntry
import com.example.data.github.RemoteFileEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryGitHubStorage : GitHubStoragePort {

    private data class StoredFile(
        val content: String,
        val sha: String
    )

    private val repos = ConcurrentHashMap<String, ConcurrentHashMap<String, StoredFile>>()
    private val commits = ConcurrentHashMap<String, MutableList<RemoteCommitEntry>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    var failPutPathPrefix: String? = null

    private fun repoMap(owner: String, repo: String) =
        repos.getOrPut("$owner/$repo") { ConcurrentHashMap() }

    override suspend fun <T> withRepoLock(owner: String, repo: String, block: suspend () -> T): T {
        val lock = locks.getOrPut("$owner/$repo") { Mutex() }
        return lock.withLock { block() }
    }

    override suspend fun getFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        ref: String?
    ): RemoteFileEntry? {
        val f = repoMap(owner, repo)[path] ?: return null
        return RemoteFileEntry(path = path, content = f.content, sha = f.sha)
    }

    override suspend fun listDirectory(
        token: String,
        owner: String,
        repo: String,
        path: String
    ): List<RemoteDirEntry> {
        val prefix = path.trimEnd('/') + "/"
        val map = repoMap(owner, repo)
        val seenDirs = mutableSetOf<String>()
        val entries = mutableListOf<RemoteDirEntry>()

        map.keys.filter { it.startsWith(prefix) }.forEach { fullPath ->
            val rest = fullPath.removePrefix(prefix)
            if (rest.contains("/")) {
                val dirName = rest.substringBefore("/")
                if (seenDirs.add(dirName)) {
                    entries.add(
                        RemoteDirEntry(
                            name = dirName,
                            path = "$prefix$dirName",
                            sha = "dir-$dirName",
                            size = 0L,
                            isDirectory = true
                        )
                    )
                }
            } else {
                val sf = map[fullPath]!!
                entries.add(
                    RemoteDirEntry(
                        name = rest,
                        path = fullPath,
                        sha = sf.sha,
                        size = sf.content.length.toLong(),
                        isDirectory = false
                    )
                )
            }
        }
        return entries.sortedWith(compareByDescending<RemoteDirEntry> { it.isDirectory }.thenBy { it.name })
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
        val failPrefix = failPutPathPrefix
        if (failPrefix != null && path.startsWith(failPrefix)) {
            throw GitHubException.NetworkOrApiException("Simulated GitHub API failure on $path")
        }
        if (validateJson && path.endsWith(".json")) {
            val t = content.trim()
            if (t.startsWith("{")) JSONObject(t) else JSONArray(t)
        }
        val map = repoMap(owner, repo)
        val existing = map[path]
        if (expectedSha != null && existing != null && existing.sha != expectedSha) {
            throw GitHubException.ConflictException("SHA conflict on $path")
        }
        val newSha = UUID.randomUUID().toString().replace("-", "").take(12)
        map[path] = StoredFile(content, newSha)
        commits.getOrPut("$owner/$repo:$path") { mutableListOf() }
            .add(0, RemoteCommitEntry(newSha, commitMessage, "test-user", "2026-12-01T00:00:00Z"))
        return newSha
    }

    override suspend fun <T> updateJsonFileWithRetry(
        token: String,
        owner: String,
        repo: String,
        path: String,
        commitMessage: String,
        transform: (currentContent: String?) -> Pair<String, T>
    ): Pair<String, T> {
        val existing = getFile(token, owner, repo, path)
        val (newContent, res) = transform(existing?.content)
        val sha = putFile(token, owner, repo, path, newContent, commitMessage, existing?.sha, true)
        return sha to res
    }

    override suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        commitMessage: String
    ) {
        repoMap(owner, repo).remove(path)
    }

    override suspend fun listCommits(
        token: String,
        owner: String,
        repo: String,
        path: String?
    ): List<RemoteCommitEntry> {
        return commits["$owner/$repo:${path ?: ""}"] ?: emptyList()
    }
}
