package com.example.pontalweb.model;

//アップロード履歴の画面の、1行分(画面に出す文字に、直したもの)
//JSP(EL式)は getXxx() で値を取るので、getter を持たせる(record の employeeName() では取れない)
public record UploadHistoryRow(Long employeeId, String employeeName, String payMonthText,
        String uploadedByName, String uploadedAtText) {

    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getPayMonthText() { return payMonthText; }
    public String getUploadedByName() { return uploadedByName; }
    public String getUploadedAtText() { return uploadedAtText; }
}
