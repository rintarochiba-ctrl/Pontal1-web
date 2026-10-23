package com.example.pontalweb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import com.example.pontalweb.exception.ApiServerException;
import com.example.pontalweb.exception.BadRequestException;
import com.example.pontalweb.exception.ConflictException;
import com.example.pontalweb.exception.ForbiddenException;
import com.example.pontalweb.exception.NotFoundException;
import com.example.pontalweb.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

//Controllerで起きた例外を、エラー画面(JSP)に変換する。画面のエラーなので、JSONではなく、HTMLを返す
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //ログインの有効期限が切れた(PontalのAPIが401を返した)、またはトークンがない → セッションを捨てて、ログイン画面へ
    @ExceptionHandler(UnauthorizedException.class)
    public String handleUnauthorized(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); //古いトークンを、使い続けさせない
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login?expired";
    }

    //権限がない → 403の画面
    @ExceptionHandler(ForbiddenException.class)
    public ModelAndView handleForbidden(ForbiddenException e) {
        return errorView("error/403", HttpStatus.FORBIDDEN, e.getMessage());
    }

    //見つからない → 404の画面
    @ExceptionHandler(NotFoundException.class)
    public ModelAndView handleNotFound(NotFoundException e) {
        return errorView("error/404", HttpStatus.NOT_FOUND, e.getMessage());
    }

    //PontalのAPIが「入力が正しくない」と返した(画面で個別に受け止めなかった場合) → 400の画面
    @ExceptionHandler(BadRequestException.class)
    public ModelAndView handleBadRequest(BadRequestException e) {
        return errorView("error/400", HttpStatus.BAD_REQUEST, e.getMessage());
    }

    //PontalのAPIが「すでにある、などで処理できない」と返した(画面で個別に受け止めなかった場合) → 409の画面
    @ExceptionHandler(ConflictException.class)
    public ModelAndView handleConflict(ConflictException e) {
        return errorView("error/409", HttpStatus.CONFLICT, e.getMessage());
    }

    //PontalのAPIの側のエラー、またはAPIに接続できなかった → 500の画面(原因は、ログにだけ残す)
    @ExceptionHandler(ApiServerException.class)
    public ModelAndView handleApiServerError(ApiServerException e) {
        log.error("PontalのAPIの呼び出しでエラーが発生しました", e);
        return errorView("error/500", HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    //URLの数字の部分に、文字が入っているなど → 400の画面
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return errorView("error/400", HttpStatus.BAD_REQUEST, null);
    }

    //それ以外の例外
    @ExceptionHandler(Exception.class)
    public ModelAndView handleOthers(Exception e) {
        //Spring MVCが投げる例外(URLが無い、パラメータが足りないなど)は、ステータスを持っているので、それに合わせる
        //(これを分けないと、存在しないURLまで、500の画面になってしまう)
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.value() == 404) {
                return errorView("error/404", HttpStatus.NOT_FOUND, null);
            }
            if (status.is4xxClientError()) {
                return errorView("error/400", HttpStatus.valueOf(status.value()), null);
            }
        }
        //予期しないエラー。原因は、ログに残して、画面には、詳しい内容を出さない
        log.error("予期しないエラーが発生しました", e);
        return errorView("error/500", HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    //エラー画面の名前と、HTTPステータス、画面に出すメッセージ(あれば)をまとめる
    private ModelAndView errorView(String viewName, HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(viewName, status);
        mav.addObject("message", message);
        return mav;
    }
}
