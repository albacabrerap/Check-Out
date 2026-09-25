package com.checkout.backend.email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailDetails {
    @NotNull
    private String recipient;
    private String msgBody;
    private String subject;
    private String attachment;
}