package ph.thecoffeejunkie.crm.shared;

import java.util.Map;

public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public DomainException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = properties;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
