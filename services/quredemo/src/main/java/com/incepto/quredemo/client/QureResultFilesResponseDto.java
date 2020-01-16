package com.incepto.quredemo.client;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QureResultFilesResponseDto {
    String sc = "";
    String gsps = "";
    QureResultFilesReportsResponseDto reports = new QureResultFilesReportsResponseDto();
    List<String> gt = new ArrayList<>();
}
