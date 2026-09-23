package com.example.mcpcatalog.rest;

import java.math.BigDecimal;

import com.example.mcpcatalog.catalog.application.CatalogItemView;
import com.example.mcpcatalog.catalog.application.CatalogPage;
import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP binding only; catalog policy and defaults belong to CatalogService. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public CatalogPage search(@RequestParam(required = false) String type,
                              @RequestParam(required = false) Boolean active,
                              @RequestParam(required = false) BigDecimal maxPrice,
                              @RequestParam(required = false) String text,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer pageSize) {
        return service.search(new CatalogSearchCriteria(type, active, maxPrice, text, page, pageSize));
    }

    @GetMapping("/{id}")
    public CatalogItemView detail(@PathVariable Long id) {
        return service.getItem(id);
    }
}
