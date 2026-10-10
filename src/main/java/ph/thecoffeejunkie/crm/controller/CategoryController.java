package ph.thecoffeejunkie.crm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import ph.thecoffeejunkie.crm.dto.request.CategoryCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.CategoryResponse;
import ph.thecoffeejunkie.crm.entity.Category;
import ph.thecoffeejunkie.crm.exception.DuplicateResourceException;
import ph.thecoffeejunkie.crm.repository.CategoryRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .toList();
    }

    @PostMapping
    public CategoryResponse addCategory(@RequestBody @Valid CategoryCreateRequest request) {
        String name = request.name().strip();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Category '" + name + "' already exists");
        }
        Category category = new Category();
        category.setName(name);
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getId(), saved.getName());
    }
}
