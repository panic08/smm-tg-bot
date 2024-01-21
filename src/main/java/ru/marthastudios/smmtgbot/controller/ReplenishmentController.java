package ru.marthastudios.smmtgbot.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.marthastudios.smmtgbot.api.payload.CrystalPayCallbackRequest;
import ru.marthastudios.smmtgbot.service.ReplenishmentService;

@RestController
@RequestMapping("/api/v1/replenishment")
@RequiredArgsConstructor
public class ReplenishmentController {
    private final ReplenishmentService replenishmentService;

    @PostMapping
    public void create(@RequestBody CrystalPayCallbackRequest crystalPayCallbackRequest) {
        replenishmentService.handleCallbackCrystalPayReplenishment(crystalPayCallbackRequest);
    }
}
