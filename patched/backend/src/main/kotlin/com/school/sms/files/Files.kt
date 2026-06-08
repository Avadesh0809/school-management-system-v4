package com.school.sms.files

import com.school.sms.dto.FileMetadataDto
import com.school.sms.entity.FileMetadata
import com.school.sms.exception.BadRequestException
import com.school.sms.exception.NotFoundException
import com.school.sms.repository.FileMetadataRepository
import com.school.sms.repository.UserRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class FileStorageService(
    @Value("\${app.storage.root}") private val root: String,
    private val metaRepo: FileMetadataRepository,
    private val users: UserRepository
) {
    lateinit var rootPath: Path

    @PostConstruct fun init() {
        rootPath = Paths.get(root).toAbsolutePath().normalize()
        Files.createDirectories(rootPath)
    }

    fun store(file: MultipartFile, ownerType: String, ownerId: Long, category: String, uploaderUsername: String?): FileMetadata {
        if (file.isEmpty) throw BadRequestException("File is empty")
        if (category.equals("profile", ignoreCase = true)) {
            val ct = file.contentType ?: ""
            if (!ct.startsWith("image/")) throw BadRequestException("Profile image must be an image file")
            if (file.size > 500L * 1024L) throw BadRequestException("Profile image must be 500KB or smaller")
            val allowed = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")
            if (allowed.none { ct.equals(it, ignoreCase = true) }) throw BadRequestException("Allowed formats: JPG, PNG, GIF, WEBP")
        }
        val ext = file.originalFilename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        val rel = Paths.get(category, ownerType, ownerId.toString(), "${UUID.randomUUID()}$ext").toString()
        val dest = rootPath.resolve(rel).normalize()
        Files.createDirectories(dest.parent)
        file.inputStream.use { Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING) }
        val uploader = uploaderUsername?.let { users.findByUsername(it).orElse(null) }
        return metaRepo.save(FileMetadata(
            ownerType = ownerType, ownerId = ownerId, category = category,
            originalName = file.originalFilename ?: "file", storedPath = rel,
            contentType = file.contentType, size = file.size, uploadedBy = uploader
        ))
    }

    fun load(id: Long): Pair<FileMetadata, UrlResource> {
        val meta = metaRepo.findById(id).orElseThrow { NotFoundException("File not found") }
        val path = rootPath.resolve(meta.storedPath).normalize()
        if (!path.startsWith(rootPath) || !Files.exists(path)) throw NotFoundException("File missing")
        return meta to UrlResource(path.toUri())
    }
}

fun FileMetadata.toDto() = FileMetadataDto(id, ownerType, ownerId, category, originalName, storedPath, contentType, size)

@RestController
@RequestMapping("/api/files")
class FileController(
    private val storage: FileStorageService,
    private val metaRepo: FileMetadataRepository
) {
    @PostMapping("/upload", consumes = ["multipart/form-data"])
    @PreAuthorize("isAuthenticated()")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam ownerType: String,
        @RequestParam ownerId: Long,
        @RequestParam category: String,
        @AuthenticationPrincipal user: UserDetails
    ) = com.school.sms.dto.ApiResponse.ok(storage.store(file, ownerType, ownerId, category, user.username).toDto())

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    fun download(@PathVariable id: Long): ResponseEntity<UrlResource> {
        val (meta, resource) = storage.load(id)
        val ct = meta.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(ct))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${meta.originalName}\"")
            .body(resource)
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("isAuthenticated()")
    fun view(@PathVariable id: Long): ResponseEntity<UrlResource> {
        val (meta, resource) = storage.load(id)
        val ct = meta.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(ct))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${meta.originalName}\"")
            .body(resource)
    }

    @GetMapping("/owner/{ownerType}/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    fun listForOwner(@PathVariable ownerType: String, @PathVariable ownerId: Long) =
        com.school.sms.dto.ApiResponse.ok(metaRepo.findByOwnerTypeAndOwnerId(ownerType, ownerId).map { it.toDto() })
}
