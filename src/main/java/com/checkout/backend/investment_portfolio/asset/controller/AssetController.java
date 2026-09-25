package com.checkout.backend.investment_portfolio.asset.controller;

import com.checkout.backend.investment_portfolio.asset.repository.AssetRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/asset")
public class AssetController {
    private final AssetRepository assetRepository;

    public AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    // ...
}
