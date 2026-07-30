package com.limou.agent.model.dto.user;

import lombok.Data;
import java.io.Serializable;

@Data
public class SetPasswordRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String newPassword;
    private String checkPassword;
}
