package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.request.ProductCreateRequest;
import ph.thecoffeejunkie.crm.dto.request.ProductUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.entity.Category;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CategoryRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    public ProductResponse save(ProductCreateRequest request) {
        Product newProduct = toProduct(request);

        return toProductResponse(productRepository.save(newProduct));
    }

    public List<ProductResponse> getAllProducts(PageRequest pageRequest) {
        return productRepository.findByActiveTrue(pageRequest).stream()
                .map(this::toProductResponse)
                .toList();
    }

    public List<ProductResponse> searchProductsByName(String productName, PageRequest pageRequest) {
        return productRepository.findByActiveTrueAndProductNameContainingIgnoreCase(productName, pageRequest).stream()
                .map(this::toProductResponse)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(this::toProductResponse)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", id);
                    return ResourceNotFoundException.of("Product", id);
                });
    }

    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findEntityById(id);

        product.setProductName(request.productName());
        product.setDescription(request.description());
        product.setCategory(findCategory(request.categoryId()));
        product.setUnit(request.unit());
        product.setPrice(request.price());
        product.setCost(request.cost());

        log.info("Updated product with id: {}", id);
        return toProductResponse(productRepository.save(product));
    }

    public ProductResponse updatePicture(Long id, MultipartFile file) {
        Product product = findEntityById(id);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Picture file is required");
        }

        String oldKey = product.getPicturePath();
        product.setPicturePath(storageService.store("products/" + id, file, StorageService.IMAGE_TYPES));
        Product saved = productRepository.save(product);
        storageService.deleteQuietly(oldKey);

        log.info("Updated picture for product with id: {}", id);
        return toProductResponse(saved);
    }

    public void delete(Long id) {

        Product product = findEntityById(id);

        product.setActive(false);
        productRepository.save(product);
    }

    private Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", id);
                    return ResourceNotFoundException.of("Product", id);
                });
    }

    private Product toProduct(ProductCreateRequest request) {
        Product product = new Product();
        product.setProductName(request.productName());
        product.setDescription(request.description());
        product.setCategory(findCategory(request.categoryId()));
        product.setUnit(request.unit());
        product.setPrice(request.price());
        product.setCost(request.cost());

        return product;
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
    }

    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getUnit(),
                product.getPrice(),
                product.getCost(),
                StorageService.url(product.getPicturePath())
        );
    }
}
