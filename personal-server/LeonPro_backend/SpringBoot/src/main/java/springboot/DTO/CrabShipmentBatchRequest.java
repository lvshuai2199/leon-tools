package springboot.DTO;

import lombok.Data;
import springboot.domain.CrabShipment;

import java.util.List;

@Data
public class CrabShipmentBatchRequest {
    private String shipDate;
    private List<CrabShipment> records;
}
