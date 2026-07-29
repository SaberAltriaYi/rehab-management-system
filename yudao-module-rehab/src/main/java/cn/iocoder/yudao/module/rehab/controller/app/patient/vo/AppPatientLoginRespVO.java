package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "患者端 APP - 登录 Response VO")
@Data
public class AppPatientLoginRespVO {

    @Schema(description = "患者端账号编号（绑定账号）", example = "90010001")
    private Long userId;

    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "过期时间")
    private LocalDateTime expiresTime;
}
