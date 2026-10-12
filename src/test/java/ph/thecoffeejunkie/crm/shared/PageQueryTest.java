package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageQueryTest {

    private static final Set<String> FIELDS = Set.of("name", "createdAt");
    private static final Sort DEFAULT = Sort.by("createdAt").descending();

    @Test
    void defaultsAndOneBasedPage() {
        var p = new PageQuery(null, null, null).toPageable(FIELDS, DEFAULT);
        assertEquals(0, p.getPageNumber());
        assertEquals(20, p.getPageSize());
        assertEquals(DEFAULT, p.getSort());
        assertEquals(1, new PageQuery(2, 5, null).toPageable(FIELDS, DEFAULT).getPageNumber());
    }

    @Test
    void parsesSortAndRejectsUnknownField() {
        var p = new PageQuery(1, 10, "name,desc").toPageable(FIELDS, DEFAULT);
        assertEquals(Sort.by(Sort.Direction.DESC, "name"), p.getSort());
        var ex = assertThrows(DomainException.class,
                () -> new PageQuery(1, 10, "password,asc").toPageable(FIELDS, DEFAULT));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.code());
    }
}
