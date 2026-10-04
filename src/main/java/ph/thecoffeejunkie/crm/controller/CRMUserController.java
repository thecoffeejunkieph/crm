package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.request.ChangePasswordRequest;
import ph.thecoffeejunkie.crm.dto.request.UserProfileUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.UserProfileResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.service.CrmUserService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class CRMUserController {

    private final CrmUserService service;

    @GetMapping
    public List<CRMUser> getAll() {
        return service.findAll();
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentUserProfile() {
        return service.getCurrentUserProfile();
    }

    @PutMapping("/me")
    public UserProfileResponse updateCurrentUserProfile(@RequestBody UserProfileUpdateRequest request) {
        return service.updateCurrentUserProfile(request);
    }

    @PutMapping("/me/password")
    public void changePassword(@RequestBody ChangePasswordRequest request) {
        service.changePassword(request);
    }

    @PostMapping(value = "/me/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse updateCurrentUserPicture(@RequestParam("file") MultipartFile file) {
        return service.updateCurrentUserPicture(file);
    }
}
