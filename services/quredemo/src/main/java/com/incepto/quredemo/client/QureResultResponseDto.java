package com.incepto.quredemo.client;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QureResultResponseDto {
    boolean success;
    String message = "";
    List<QureResultTagResponseDto> tags = new ArrayList<>();
    QureResultFilesResponseDto files = new QureResultFilesResponseDto();
    boolean integrity;
}
