package com.example.pontalweb.controller;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipSummary;
import com.example.pontalweb.client.SessionKeys;
import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.UnauthorizedException;
import com.example.pontalweb.model.LoginUser;

//給与明細(ログイン中の本人の分)。一覧と、PDFのダウンロード
@Controller
public class SalarySlipController {

    private final PontalApiClient api;

    public SalarySlipController(PontalApiClient api) {
        this.api = api;
    }

    //自分の給与明細の一覧。APIは、新しい年月の順に返す
    @GetMapping("/salary-slips")
    public String list(@SessionAttribute(name = SessionKeys.LOGIN_USER, required = false) LoginUser loginUser,
            Model model) {
        if (loginUser == null) {
            throw new UnauthorizedException("ログインが必要です"); //ログイン画面に戻される(GlobalExceptionHandler)
        }
        List<SalarySlipSummary> slips = api.get("/api/employees/{id}/salary-slips",
                new ParameterizedTypeReference<List<SalarySlipSummary>>() { }, loginUser.getEmployeeId());
        model.addAttribute("slips", slips);
        return "salary-slips";
    }

    //給与明細のPDFのダウンロード。APIが、本人かHR管理者かを確かめる(他人の分は、403の画面になる)
    //payMonth は、保存するファイル名に使うだけ(YYYY-MM の形のときだけ使い、違えば、明細のidで代用する)
    @GetMapping("/salary-slips/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam(required = false) String payMonth) {
        byte[] pdf = api.getBytes("/api/salary-slips/{id}/download", id);
        if (pdf == null) {
            throw new ApiServerException("給与明細のPDFを取得できませんでした");
        }
        String fileName = payMonth != null && payMonth.matches("\\d{4}-\\d{2}")
                ? "salary-slip_" + payMonth + ".pdf"
                : "salary-slip_" + id + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                //ブラウザに、「保存するファイル」として扱わせて、ファイル名も伝える
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                //機密の書類なので、ブラウザや途中の経路に、保存(キャッシュ)させない
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }
}
