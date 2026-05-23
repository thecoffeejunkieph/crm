package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.ProductCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.repository.ProductRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse save(ProductCreateRequest request) {
        Product newProduct = toProduct(request);

        return toProductResponse(productRepository.save(newProduct));
    }

    public List<ProductResponse> getAllProducts(PageRequest pageRequest) {
        return productRepository.findAll(pageRequest).stream()
                .map(this::toProductResponse)
                .toList();
    }

    public List<ProductResponse> searchProductsByName(String productName, PageRequest pageRequest) {
        return productRepository.findByProductNameContainingIgnoreCase(productName, pageRequest).stream()
                .map(this::toProductResponse)
                .toList();
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
                product.getPrice()
        );
    }
}
