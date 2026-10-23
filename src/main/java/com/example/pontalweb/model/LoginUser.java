package com.example.pontalweb.model;

//ログイン中の社員の情報。画面(JSP)で、名前の表示と、メニューの出し分けに使う
//JSPのEL式は、getterで値を取り出す(${loginUser.name} は getName() を呼ぶ)。真偽値は isXxx() で、${loginUser.systemAdmin} と書く
public class LoginUser {

    private final Long employeeId;
    private final String name;
    private final boolean systemAdmin;
    private final boolean hrAdmin;

    public LoginUser(Long employeeId, String name, boolean systemAdmin, boolean hrAdmin) {
        this.employeeId = employeeId;
        this.name = name;
        this.systemAdmin = systemAdmin;
        this.hrAdmin = hrAdmin;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemAdmin() {
        return systemAdmin;
    }

    public boolean isHrAdmin() {
        return hrAdmin;
    }

    //名前の頭文字。顔写真の代わりに、丸いアイコンに出す
    public String getInitial() {
        return name == null || name.isEmpty() ? "" : name.substring(0, 1);
    }
}
