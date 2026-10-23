package com.example.pontalweb.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

//Controllerより手前で起きたエラー(フィルターでのエラーなど)を、エラー画面にする
//Spring Bootの標準のエラー画面は、JSPを自動では使わないので、自分で /error を受けて、画面の名前を決める
//(Controllerの中の例外は、GlobalExceptionHandlerが先に受ける)
@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public ModelAndView error(HttpServletRequest request) {
        //コンテナ(Tomcat)が、エラーのステータスを、リクエストの属性に入れている
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = value instanceof Integer i ? i : 500;

        return switch (code) {
            case 400 -> new ModelAndView("error/400", HttpStatus.BAD_REQUEST);
            case 403 -> new ModelAndView("error/403", HttpStatus.FORBIDDEN);
            case 404 -> new ModelAndView("error/404", HttpStatus.NOT_FOUND);
            default -> new ModelAndView("error/500", HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }
}
