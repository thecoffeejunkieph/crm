package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.request.ProductCreateRequest;
import ph.thecoffeejunkie.crm.dto.request.ProductUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.exception.FileStorageException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.ProductRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Map<String, String> ALLOWED_IMAGE_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final ProductRepository productRepository;

    @Value("${app.storage.root-dir}")
    private String storageRootDir;

    @Value("${app.storage.public-path}")
    private String storagePublicPath;

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
        product.setUnit(request.unit());
        product.setPrice(request.price());

        log.info("Updated product with id: {}", id);
        return toProductResponse(productRepository.save(product));
    }

    public ProductResponse updatePicture(Long id, MultipartFile file) {
        Product product = findEntityById(id);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Picture file is required");
        }

        String extension = ALLOWED_IMAGE_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidRequestException("Unsupported file type. Allowed types: JPEG, PNG, WEBP");
        }

        product.setPicturePath(writeProductPicture(id, extension, file));

        log.info("Updated picture for product with id: {}", id);
        return toProductResponse(productRepository.save(product));
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

    private String writeProductPicture(Long productId, String extension, MultipartFile file) {
        try {
            Path targetDir = Paths.get(storageRootDir, "products", "pictures");
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(productId + extension);
            Files.write(targetFile, file.getBytes());

            return storagePublicPath + "/products/pictures/" + productId + extension;
        } catch (IOException e) {
            log.error("Failed to store picture for product {}", productId, e);
            throw new FileStorageException("Failed to store product picture", e);
        }
    }

    private Product toProduct(ProductCreateRequest request) {
        Product product = new Product();
        product.setProductName(request.productName());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setPrice(request.price());

        return product;
    }

    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getUnit(),
                product.getPrice(),
                product.getPicturePath()
        );
    }
}
