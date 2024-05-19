package semohan.owner.domain.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import semohan.owner.domain.restaurant.domain.Address;

@Data
public class RestaurantInfoUpdateDto {

    @NotNull
    private String phoneNumber;

    @NotNull
    private String businessHours;

    @NotNull
    private String price;

    private String postCode;

    private String address;

    private String detailedAddress;
}
