package com.lumira.backend.resource;

import com.lumira.backend.security.AuthenticatedUser;
import com.lumira.backend.security.CurrentUser;
import com.lumira.backend.security.RequireAuth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequireAuth
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service) { this.service = service; }

    @PostMapping(path = "/v1/cards/{cardId}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse upload(@PathVariable UUID cardId, @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title, @CurrentUser AuthenticatedUser user) {
        return service.createForCard(cardId, user.userId(), file, title);
    }

    @GetMapping("/v1/cards/{cardId}/resources")
    public List<ResourceResponse> list(@PathVariable UUID cardId, @CurrentUser AuthenticatedUser user) {
        return service.listForCard(cardId, user.userId());
    }

    @GetMapping("/v1/resources/{resourceId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID resourceId, @CurrentUser AuthenticatedUser user) {
        return service.download(resourceId, user.userId());
    }

    @PostMapping("/v1/resources/{resourceId}/reprocess")
    public ResourceResponse reprocess(@PathVariable UUID resourceId, @CurrentUser AuthenticatedUser user) {
        return service.reprocess(resourceId, user.userId());
    }
}
