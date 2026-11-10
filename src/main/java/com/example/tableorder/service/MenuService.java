package com.example.tableorder.service;

import com.example.tableorder.exception.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.tableorder.dto.*;
import com.example.tableorder.entity.menu.MenuItemI18n;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tableorder.entity.category.Category;
import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.entity.menu.MenuOption;
import com.example.tableorder.entity.menu.MenuOptionI18n;
import com.example.tableorder.repository.CategoryRepository;
import com.example.tableorder.repository.MenuRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;

    public MenuResponse getMenus(Long storeId, Long tableId, String lang) {
        log.info("메뉴 조회 요청: storeId={}, tableId={}, lang={}", storeId, tableId, lang);

        // 기본 언어 설정 (한국어)
        String language = (lang != null && !lang.trim().isEmpty()) ? lang : "ko";

        // 스토어의 모든 메뉴 조회
        List<MenuItem> menuItems = menuRepository.findByCategoryStoreIdAndSoldOutFalseOrderByCategoryIdAscIdAsc(storeId);

        // 카테고리별로 메뉴 그룹화
        Map<Category, List<MenuItem>> menuByCategory = menuItems.stream()
                .collect(Collectors.groupingBy(MenuItem::getCategory));

        // 카테고리별 메뉴 응답 생성
        List<CategoryMenuResponse> categoryMenus = menuByCategory.entrySet().stream()
                .map(entry -> createCategoryMenuDto(entry.getKey(), entry.getValue(), language))
                .sorted(Comparator.comparing(CategoryMenuResponse::getCategoryId))
                .collect(Collectors.toList());

        return MenuResponse.builder()
                .storeId(storeId)
                .tableId(tableId)
                .language(language)
                .categories(categoryMenus)
                .build();
    }

    public MenuResponse getMenusByCategory(Long storeId, Long tableId, Long categoryId, String lang) {
        log.info("카테고리별 메뉴 조회 요청: storeId={}, tableId={}, categoryId={}, lang={}",
                storeId, tableId, categoryId, lang);

        String language = (lang != null && !lang.trim().isEmpty()) ? lang : "ko";

        // 특정 카테고리의 메뉴만 조회
        List<MenuItem> menuItems = menuRepository.findByCategoryIdAndCategoryStoreIdAndSoldOutFalseOrderByIdAsc(categoryId, storeId);

        // 카테고리 정보 조회
        Category category = categoryRepository.findByIdAndStoreId(categoryId, storeId)
                .orElseThrow(() -> new NotFoundException("카테고리를 찾을 수 없습니다: " + categoryId));

        // 카테고리별 메뉴 응답 생성
        List<CategoryMenuResponse> categoryMenus = Collections.singletonList(
                createCategoryMenuDto(category, menuItems, language) // DTO 매핑
        );

        return MenuResponse.builder()
                .storeId(storeId)
                .tableId(tableId)
                .language(language)
                .categories(categoryMenus)
                .build();
    }

    // 계층적 DTO 매핑
    private CategoryMenuResponse createCategoryMenuDto(Category category, List<MenuItem> menuItems, String lang) {
        List<MenuItemResponse> menuItemDtos = menuItems.stream()
                .map(menuItem -> createMenuItemDto(menuItem, lang))
                .collect(Collectors.toList());

        return CategoryMenuResponse.builder()
                .categoryId(category.getId())
                .categoryName(getCategoryNameByLanguage(category, lang))
                .menuItems(menuItemDtos)
                .build();
    }

    private MenuItemResponse createMenuItemDto(MenuItem menuItem, String lang) {
        // 디버깅 로그 추가
        log.debug("메뉴 번역 정보: menuId={}, lang={}, translations={}",
                menuItem.getId(), lang, menuItem.getTranslations());

        // 해당 언어의 번역 정보 찾기
        String name = menuItem.getTranslations().stream()
                .filter(t -> t.getLang().equals(lang))
                .findFirst()
                .map(MenuItemI18n::getName)
                .orElse("이름 없음");

        String description = menuItem.getTranslations().stream()
                .filter(t -> t.getLang().equals(lang))
                .findFirst()
                .map(MenuItemI18n::getDescription)
                .orElse("");

        // 메뉴 옵션 정보 생성
        List<MenuOptionResponse> optionDtos = new ArrayList<>();
        if (menuItem.getOptions() != null) {
            optionDtos = menuItem.getOptions().stream()
                    .map(option -> createMenuOptionDto(option, lang))
                    .collect(Collectors.toList());
        }

        return MenuItemResponse.builder()
                .menuId(menuItem.getId())
                .name(name)
                .description(description)
                .price(menuItem.getPrice())
                .imageUrl(menuItem.getImageUrl())
                .soldOut(menuItem.getSoldOut())
                .quantity(menuItem.getQuantity())
                .enableInventory(menuItem.getEnableInventory())
                .prepTimeMin(menuItem.getPrepTimeMin())
                .options(optionDtos)
                .build();
    }

    private MenuOptionResponse createMenuOptionDto(MenuOption option, String lang) {
        // 해당 언어의 번역 정보 찾기
        String name = option.getTranslations().stream()
                .filter(t -> t.getLang().equals(lang))
                .findFirst()
                .map(MenuOptionI18n::getName)
                .orElse("옵션 이름 없음");

        String description = option.getTranslations().stream()
                .filter(t -> t.getLang().equals(lang))
                .findFirst()
                .map(MenuOptionI18n::getDescription)
                .orElse("");

        return MenuOptionResponse.builder()
                .optionId(option.getId())
                .name(name)
                .description(description)
                .price(option.getExtraPrice())
                .required(option.getRequired())
                .build();
    }

    private String getCategoryNameByLanguage(Category category, String lang) {
        switch (lang) {
            case "ko":
                return category.getNameKo();
            case "en":
                return category.getNameEn() != null ? category.getNameEn() : category.getNameKo();
            case "zh":
                return category.getNameZh() != null ? category.getNameZh() : category.getNameKo();
            case "ja":
                return category.getNameJa() != null ? category.getNameJa() : category.getNameKo();
            default:
                return category.getNameKo();
        }
    }
}
