package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.request.ChangePasswordRequest;
import ph.thecoffeejunkie.crm.dto.request.UserProfileUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.UserProfileResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.exception.FileStorageException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrmUserService {

    private static final Map<String, String> ALLOWED_IMAGE_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final CRMUserRepository crmUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.storage.root-dir}")
    private String storageRootDir;

    @Value("${app.storage.public-path}")
    private String storagePublicPath;

    public List<CRMUser> findAll() {
        return crmUserRepository.findAll();
    }

    public UserProfileResponse getCurrentUserProfile() {
        return toUserProfileResponse(resolveCurrentUser());
    }

    public UserProfileResponse updateCurrentUserProfile(UserProfileUpdateRequest request) {
        CRMUser user = resolveCurrentUser();

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setCellphoneNumber(request.cellphoneNumber());
        user.setAddress(request.address());
        user.setBirthday(request.birthday());

        CRMUser saved = crmUserRepository.save(user);
        log.info("Updated profile for user {}", saved.getEmail());

        return toUserProfileResponse(saved);
    }

    public void changePassword(ChangePasswordRequest request) {
        CRMUser user = resolveCurrentUser();

        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new InvalidRequestException("New password is required");
        }
        if (request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        crmUserRepository.save(user);

        log.info("Password changed for user {}", user.getEmail());
    }

    public UserProfileResponse updateCurrentUserPicture(MultipartFile file) {
        CRMUser user = resolveCurrentUser();

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Picture file is required");
        }

        String extension = ALLOWED_IMAGE_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidRequestException("Unsupported file type. Allowed types: JPEG, PNG, WEBP");
        }

        user.setPicturePath(writeProfilePicture(user.getEmail(), extension, file));
        CRMUser saved = crmUserRepository.save(user);

        log.info("Updated profile picture for user {}", saved.getEmail());
        return toUserProfileResponse(saved);
    }

    private String writeProfilePicture(String email, String extension, MultipartFile file) {
        try {
            Path targetDir = Paths.get(storageRootDir, "users", "profile-pictures");
            Files.createDirectories(targetDir);

            String safeName = email.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path targetFile = targetDir.resolve(safeName + extension);
            Files.write(targetFile, file.getBytes());

            return storagePublicPath + "/users/profile-pictures/" + safeName + extension;
        } catch (IOException e) {
            log.error("Failed to store profile picture for user {}", email, e);
            throw new FileStorageException("Failed to store profile picture", e);
        }
    }

    private CRMUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidRequestException("No authenticated user");
        }

        return crmUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> ResourceNotFoundException.of("User", authentication.getName()));
    }

    private UserProfileResponse toUserProfileResponse(CRMUser user) {
        return new UserProfileResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCellphoneNumber(),
                user.getAddress(),
                user.getBirthday(),
                Arrays.asList(user.getRoles().split(",")),
                user.getPicturePath()
        );
    }
}
