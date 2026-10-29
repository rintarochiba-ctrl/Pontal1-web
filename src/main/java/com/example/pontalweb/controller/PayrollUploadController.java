package com.example.pontalweb.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.pontalweb.client.EmployeeOptions;
import com.example.pontalweb.client.PontalApiClient;
import com.example.pontalweb.client.SalarySlipUploadResult;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.NotFoundException;

//給与明細のアップロード(HR管理者だけ。/payroll/** は、SecurityConfigで制限)
//社員・対象年月・PDFを選んで、APIに送る。同じ社員・年月が、すでにあるときは、「上書きする」にチェックして送り直す
@Controller
public class PayrollUploadController {

    private static final String FORM_VIEW = "payroll-upload";

    private final PontalApiClient api;

    //アップロードできるファイルの大きさの上限(application.properties の設定。画面に出すために、読む)
    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private String maxFileSize;

    public PayrollUploadController(PontalApiClient api) {
        this.api = api;
    }

    @GetMapping("/payroll/upload")
    public String form(Model model) {
        model.addAttribute("options", EmployeeOptions.loadAll(api));
        model.addAttribute("maxFileSize", maxFileSize);
        return FORM_VIEW;
    }

    @PostMapping("/payroll/upload")
    public String upload(@RequestParam Long employeeId,
            @RequestParam String payMonth,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @RequestParam MultipartFile file,
            Model model,
            RedirectAttributes redirectAttributes) throws IOException {
        String error = validate(file);

        if (error == null) {
            //APIに送る内容。ファイル名は、画面の側で、固定の名前にする(元のファイル名は、APIが使わない。変な名前を、渡さないため)
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("employeeId", String.valueOf(employeeId));
            parts.add("payMonth", payMonth.trim());
            parts.add("overwrite", String.valueOf(overwrite));
            parts.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return "salary-slip.pdf";
                }
            });

            try {
                SalarySlipUploadResult result = api.postMultipart("/api/salary-slips", parts, SalarySlipUploadResult.class);
                redirectAttributes.addFlashAttribute("successMessage",
                        "給与明細をアップロードしました(" + result.payMonth() + ")");
                //アップロードした社員の、一覧に移動する(アップロードできたことを、すぐに確認できる)
                return "redirect:/payroll?employeeId=" + result.employeeId();
            } catch (ConflictException e) {
                //同じ社員・年月が、すでにある(上書きの指定がないとき)
                error = e.getMessage() + "。上書きする場合は、「上書きする」にチェックを入れて、もう一度、ファイルを選んで送信してください";
            } catch (BadRequestException | NotFoundException e) {
                //年月の形の間違い、社員が見つからない、など(APIのメッセージは、日本語)
                error = e.getMessage();
            }
        }

        //入力し直せば通るものは、エラー画面ではなく、入力画面に、メッセージつきで戻す(ファイルだけは、選び直してもらう)
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", employeeId);
        form.put("payMonth", payMonth);
        form.put("overwrite", overwrite);
        model.addAttribute("form", form);
        model.addAttribute("errorMessage", error);
        model.addAttribute("options", EmployeeOptions.loadAll(api));
        model.addAttribute("maxFileSize", maxFileSize);
        return FORM_VIEW;
    }

    //ファイルの確認。問題がなければ null、あれば、画面に出すメッセージ
    //拡張子だけでなく、中身の先頭(%PDF-)も見る(PDFでないファイルの、名前だけを変えたものを、止めるため)
    private String validate(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return "PDFファイルを選択してください";
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            return "PDFファイル(.pdf)を選択してください";
        }
        byte[] head = file.getInputStream().readNBytes(5);
        if (!new String(head, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            return "PDFファイルではないようです。ファイルを確認してください";
        }
        return null;
    }
}
