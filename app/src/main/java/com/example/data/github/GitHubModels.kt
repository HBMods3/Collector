package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "interval") val interval: Int
)

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "scope") val scope: String? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    @Json(name = "interval") val interval: Int? = null
)

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
    @Json(name = "id") val id: Long,
    @Json(name = "login") val login: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubOwnerDto(
    @Json(name = "login") val login: String
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "private") val isPrivate: Boolean,
    @Json(name = "default_branch") val defaultBranch: String? = "main",
    @Json(name = "owner") val owner: GitHubOwnerDto,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String = "SLR Note Bu Private Shop Database",
    @Json(name = "private") val isPrivate: Boolean = true,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitHubContentDto(
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "sha") val sha: String,
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "type") val type: String, // "file" or "dir"
    @Json(name = "content") val content: String? = null,
    @Json(name = "encoding") val encoding: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class PutFileRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val contentBase64: String,
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "branch") val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteFileRequest(
    @Json(name = "message") val message: String,
    @Json(name = "sha") val sha: String,
    @Json(name = "branch") val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitRefDto(
    @Json(name = "sha") val sha: String,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class PutFileResponse(
    @Json(name = "content") val content: GitHubContentDto? = null,
    @Json(name = "commit") val commit: GitHubCommitRefDto? = null
)

@JsonClass(generateAdapter = true)
data class CommitAuthorDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "date") val date: String? = null
)

@JsonClass(generateAdapter = true)
data class CommitDetailDto(
    @Json(name = "message") val message: String? = null,
    @Json(name = "author") val author: CommitAuthorDto? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItemDto(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commit: CommitDetailDto
)
