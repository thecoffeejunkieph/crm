package ph.thecoffeejunkie.crm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.request.ProductCreateRequest;
import ph.thecoffeejunkie.crm.dto.request.ProductUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.ProductResponse;
import ph.thecoffeejunkie.crm.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getAllProducts(@RequestParam(defaultValue = "0") int pageNumber,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String productName) {

        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);

        if (Strings.isNotEmpty(productName)) {
            return productService.searchProductsByName(productName, pageRequest);
        }

        return productService.getAllProducts(pageRequest);
    }

    @PostMapping
    public ProductResponse addProduct(@RequestBody @Valid ProductCreateRequest request){
        return productService.save(request);
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @RequestBody @Valid ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @PostMapping(value = "/{id}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductResponse updateProductPicture(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return productService.updatePicture(id, file);
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.delete(id);
    }

}
