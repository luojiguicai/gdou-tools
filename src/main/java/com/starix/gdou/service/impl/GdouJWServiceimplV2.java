package com.starix.gdou.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.starix.gdou.dto.LoginResultV2;
import com.starix.gdou.dto.request.ExamQueryRquestDTO;
import com.starix.gdou.dto.request.ScoreQueryRquestDTO;
import com.starix.gdou.dto.response.ExamQueryResponseDTO;
import com.starix.gdou.dto.response.ScoreQueryResponseDTO;
import com.starix.gdou.dto.response.YearOptionListResponseDTO;
import com.starix.gdou.entity.Student;
import com.starix.gdou.exception.CustomException;
import com.starix.gdou.repository.StudentRepository;
import com.starix.gdou.response.CommonResult;
import com.starix.gdou.service.GdouJWServiceV2;
import com.starix.gdou.utils.HttpClientUtil;
import com.starix.gdou.utils.RSAUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.cookie.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 适配新版教务系统的service实现类
 * @author shiwenjie
 * @created 2020/6/28 5:16 下午
 **/
@Service
@Slf4j
public class GdouJWServiceimplV2 implements GdouJWServiceV2 {

    // 教务系统地址
    @Value("${gdou.jw-url}")
    private String JW_URL;

    @Autowired
    private StudentRepository studentRepository;


    @Override
    public LoginResultV2 login(String username, String password) throws Exception {
        // 一次完整流程中的所有请求都需要使用同一个httpclient实例，保持cookie同步
        HttpClientUtil httpClient = new HttpClientUtil();
        jwLogin(httpClient, username, password);
        List<Cookie> cookies = httpClient.getCookieStore().getCookies();
        return new LoginResultV2(cookies, username);
    }

    /**
     * 执行教务系统登录
     */
    private void jwLogin(HttpClientUtil httpClient, String useranme, String password) throws Exception{
        // 获取publickey，用于密码rsa加密
        String resultStr = httpClient.doGet(JW_URL + "/xtgl/login_getPublicKey.html");
        JSONObject json = JSON.parseObject(resultStr);
        String modulus = json.getString("modulus");
        String exponent = json.getString("exponent");
        // 密码加密
        String encryptPassword = RSAUtil.encrypt(modulus, exponent, password);
        // 如果当前请求直接重定向进入教务系统首页，说明是已登录状态，无需再登录
        // 当评价执行过程出现异常导致webvpn未正确注销时，相同账号再次登录教务系统就会出现这个情况
        String jwLoginUrl = JW_URL + "/xtgl/login_slogin.html";
        resultStr = httpClient.doGet(jwLoginUrl);
        if (!resultStr.contains("用户登录")){
            log.info("[{}]当前教务系统账号已处于登录状态", useranme);
            return;
        }
        // 获取页面csrftoken参数
        Document document = Jsoup.parse(resultStr);
        String csrftoken = document.getElementById("csrftoken").val();
        Map<String, String> data = new HashMap<>();
        data.put("csrftoken", csrftoken);
        data.put("yhm", useranme);
        data.put("mm", encryptPassword);
        resultStr = httpClient.doPost(jwLoginUrl, data);
        if (resultStr.contains("用户名或密码不正确")){
            throw new CustomException(CommonResult.failed("用户名或密码不正确"));
        }
        log.info("[{}]教务系统登录成功", useranme);
    }

    @Override
    public LoginResultV2 loginByOpenid(String openid) throws Exception {
        Student student = studentRepository.findByOpenid(openid);
        if (student == null) {
            throw new CustomException(CommonResult.failed("未绑定学号"));
        }
        log.info("[{}]已绑定用户自动登录", student.getUsername());
        return login(student.getUsername(), student.getPassword());
    }

    @Override
    public List<ScoreQueryResponseDTO> queryScore(ScoreQueryRquestDTO scoreQueryRquestDTO) throws IOException {
        HttpClientUtil httpClient = new HttpClientUtil();
        addCookie(httpClient, scoreQueryRquestDTO.getCookies());
        Map<String, String> data = new HashMap<>();
        data.put("xnm", scoreQueryRquestDTO.getYear());
        data.put("xqm", scoreQueryRquestDTO.getSemester());
        data.put("_search", "false");
        data.put("queryModel.showCount", "15");
        data.put("queryModel.currentPage", "1");
        data.put("queryModel.sortName", "");
        data.put("queryModel.sortOrder", "asc");
        // data.put("nd", "1594138638066");
        // data.put("time", "3");
        String resultStr = httpClient.doPost(JW_URL + "/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005", data);
        JSONObject json = JSON.parseObject(resultStr);
        List<ScoreQueryResponseDTO> resultList = buildScoreQueryResponseDTOList(json);
        Integer totalPage = json.getInteger("totalPage");
        // 获取剩余页数据
        if (totalPage > 1){
            for (int p = 2; p <= totalPage; p++) {
                data.put("queryModel.currentPage", p + "");
                resultStr = httpClient.doPost(JW_URL + "/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005", data);
                json = JSON.parseObject(resultStr);
                resultList.addAll(buildScoreQueryResponseDTOList(json));
            }
        }
        return resultList;
    }

    @Override
    public ExamQueryResponseDTO queryExam(ExamQueryRquestDTO examQueryRquestDTO) {
        return null;
    }

    @Override
    public YearOptionListResponseDTO getSocreYearOptionList(List<Cookie> cookies) throws Exception {
        HttpClientUtil httpClient = new HttpClientUtil();
        addCookie(httpClient, cookies);
        String resultStr = httpClient.doGet(JW_URL + "/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005");
        Document document = Jsoup.parse(resultStr);
        return buildYearOptionListResponseDTO(document);
    }

    @Override
    public List<String> getExamYearOptionList(List<Cookie> cookies) throws Exception {
        return null;
    }


    private YearOptionListResponseDTO buildYearOptionListResponseDTO(Document document){
        //获取年份下拉列表数据
        Elements yearOptions = document.getElementById("xnm").getElementsByTag("option");
        int selectedYear = 0;
        List<String> yearOptionValueList = new ArrayList<>();
        List<String> yearOptionTextList = new ArrayList<>();
        for (int i = 0; i < yearOptions.size(); i++) {
            yearOptionValueList.add(yearOptions.get(i).val());
            yearOptionTextList.add(yearOptions.get(i).text());
            if (yearOptions.get(i).hasAttr("selected")){
                selectedYear = i;
            }
        }
        //获取学期下拉列表数据（这里暂时只是获取选中的下标，选项数据前端写死）
        Elements semesterOptions = document.getElementById("xqm").getElementsByTag("option");
        int selectedSemester = 0;
        List<String> semeterOptionValueList = new ArrayList<>();
        List<String> semeterOptionTextList = new ArrayList<>();
        for (int i = 0; i < semesterOptions.size(); i++) {
            semeterOptionValueList.add(semesterOptions.get(i).val());
            semeterOptionTextList.add(semesterOptions.get(i).text());
            if (semesterOptions.get(i).hasAttr("selected")){
                selectedSemester = i;
            }
        }
        YearOptionListResponseDTO responseDTO = YearOptionListResponseDTO.builder()
                .yearValueList(yearOptionValueList)
                .yearTextList(yearOptionTextList)
                .semesterValueList(semeterOptionValueList)
                .semesterTextList(semeterOptionTextList)
                .selectedYear(selectedYear)
                .selectedSemester(selectedSemester)
                .build();
        return responseDTO;
    }

    private void addCookie(HttpClientUtil httpClient, List<Cookie> cookies){
        cookies.forEach(cookie -> httpClient.getCookieStore().addCookie(cookie));
    }

    private List<ScoreQueryResponseDTO> buildScoreQueryResponseDTOList(JSONObject json){
        JSONArray items = json.getJSONArray("items");
        List<ScoreQueryResponseDTO> resultList = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ScoreQueryResponseDTO scoreQueryResponseDTO = ScoreQueryResponseDTO.builder()
                    .courseName(items.getJSONObject(i).getString("kcmc"))
                    .credit(items.getJSONObject(i).getString("xf"))
                    .score(items.getJSONObject(i).getString("cj"))
                    .gpa(items.getJSONObject(i).getString("jd"))
                    .property(items.getJSONObject(i).getString("kcxzmc"))
                    .category(items.getJSONObject(i).getString("kclbmc"))
                    .belongTo(items.getJSONObject(i).getString("kcgsmc"))
                    .build();
            resultList.add(scoreQueryResponseDTO);
        }
        return resultList;
    }

}