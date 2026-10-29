package com.example.pontalweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.pontalweb.client.EmployeeOptionPage;
import com.example.pontalweb.client.SalarySlipUploadResult;
import com.example.pontalweb.model.LoginUser;

//給与明細のアップロードを、実際に起動したアプリ(Tomcat + JSP)で確認する結合テスト
//MockMvcでは確認できない、次のことを確かめる
//  ・ファイルを送るフォームで、CSRFの確認用トークンが、ちゃんと通ること(通らないと、アップロードが、すべて403になる)
//  ・ファイルが、上限より大きいときの動き
class PayrollUploadPageIntegrationTest extends LoggedInPageTestBase {

    private static final byte[] PDF = "%PDF-1.4 dummy".getBytes();

    private String hrAdminCookie;

    @BeforeEach
    void setUp() {
        when(api.get(eq("/api/employees?page={page}&size={size}"), eq(EmployeeOptionPage.class), eq(0), eq(100)))
                .thenReturn(new EmployeeOptionPage(List.of(new EmployeeOptionPage.Option(3L, "山田太郎", "開発部")), null));
        hrAdminCookie = loginAs(new LoginUser(1L, "人事担当", false, true), "ROLE_USER", "ROLE_HR_ADMIN");
    }

    //入力画面を開いて、CSRFの確認用トークンを取り出す
    private String csrfToken() {
        String body = openAs(hrAdminCookie, "/payroll/upload");
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    //ファイルつきのフォームを、送る(csrf が null なら、確認用トークンを付けない)
    private ResponseEntity<String> post(String csrf, byte[] fileBytes) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        if (csrf != null) {
            form.add("_csrf", csrf);
        }
        form.add("employeeId", "3");
        form.add("payMonth", "2024-09");
        form.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "pay.pdf";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, hrAdminCookie);
        return rest.exchange("/payroll/upload", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    @Test
    void uploadForm_showsEmployeeSelect_monthInput_fileInput_andMultipartEnctype() {
        String body = openAs(hrAdminCookie, "/payroll/upload");

        assertThat(body).contains("enctype=\"multipart/form-data\"");
        assertThat(body).contains("山田太郎(開発部)", "type=\"month\"", "type=\"file\"", "name=\"overwrite\"");
        assertThat(body).contains("1MB まで"); //上限を、画面に出す
    }

    @Test
    void upload_withCsrfToken_reachesApi_andRedirectsToEmployeesList() {
        when(api.postMultipart(eq("/api/salary-slips"), any(), eq(SalarySlipUploadResult.class)))
                .thenReturn(new SalarySlipUploadResult(10L, 3L, "2024-09"));

        ResponseEntity<String> response = post(csrfToken(), PDF);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).endsWith("/payroll?employeeId=3");
        //Tomcatを通って、ファイルの中身と、フォームの項目が、APIに渡る内容として、そろっていること
        ArgumentCaptor<MultiValueMap<String, Object>> parts = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(api).postMultipart(eq("/api/salary-slips"), parts.capture(), eq(SalarySlipUploadResult.class));
        assertThat(parts.getValue().getFirst("employeeId")).isEqualTo("3");
        assertThat(parts.getValue().getFirst("payMonth")).isEqualTo("2024-09");
        assertThat(((ByteArrayResource) parts.getValue().getFirst("file")).getByteArray()).isEqualTo(PDF);
    }

    @Test
    void upload_withoutCsrfToken_isRejected_andDoesNotReachApi() {
        ResponseEntity<String> response = post(null, PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        org.mockito.Mockito.verify(api, org.mockito.Mockito.never()).postMultipart(any(), any(), any());
    }

    @Test
    void upload_whenFileIsLargerThanLimit_doesNotReachApi_andTellsTheUser() {
        byte[] tooLarge = new byte[2 * 1024 * 1024]; //上限(1MB)の2倍
        System.arraycopy("%PDF-".getBytes(), 0, tooLarge, 0, 5);

        ResponseEntity<String> response = post(csrfToken(), tooLarge);

        //413で断られて、利用者に、理由(上限)が伝わる
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("ファイルが大きすぎます(上限: 1MB)");
        org.mockito.Mockito.verify(api, org.mockito.Mockito.never()).postMultipart(any(), any(), any());
    }
}
