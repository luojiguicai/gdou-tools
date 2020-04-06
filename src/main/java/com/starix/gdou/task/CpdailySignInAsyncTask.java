package com.starix.gdou.task;

import com.starix.gdou.entity.CpdailyUser;
import com.starix.gdou.exception.CustomException;
import com.starix.gdou.response.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Tobu
 * @date 2020-04-06 12:03
 */
@Slf4j
@Component
public class CpdailySignInAsyncTask {

    //python脚本绝对路径
    @Value("${python.path}")
    private String PYTHON_PATH_AUTH;
    //python脚本命令行输出的编码
    @Value("${python.output-encoding}")
    private String OUTPUT_ENCODING;

    @Async
    public Future<String> doSignIn(CpdailyUser cpdailyUser) throws Exception{
        String[] cmd = new String[16];
        cmd[0] = "python";
        cmd[1] = "cpdaily_sign_in.py";
        cmd[2] = "--username";
        cmd[3] = cpdailyUser.getUsername();
        cmd[4] = "--password";
        cmd[5] = cpdailyUser.getPassword();
        cmd[6] = "--longitude";
        cmd[7] = cpdailyUser.getLongitude();
        cmd[8] = "--latitude";
        cmd[9] = cpdailyUser.getLatitude();
        cmd[10] = "--abnormalReason";
        cmd[11] = cpdailyUser.getAbnormalreason();
        cmd[12] = "--position";
        cmd[13] = cpdailyUser.getPosition();
        cmd[14] = "--email";
        cmd[15] = cpdailyUser.getEmail();
        Process process = Runtime.getRuntime().exec(cmd, null, new File(PYTHON_PATH_AUTH));
        int status = process.waitFor();
        if (status != 0){
            log.error("Python签到脚本执行出错，status:{}", status);
            throw new CustomException(CommonResult.failed("Python签到脚本执行出错"));
        }
        InputStream in = process.getInputStream();
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(in, OUTPUT_ENCODING));
        String line;
        List<String> resultLines = new ArrayList<>();
        while ((line = buffReader.readLine()) != null){
            resultLines.add(line);
        }
        //简单关闭流，暂时不考虑异常
        buffReader.close();
        in.close();

        if (resultLines.contains("result-签到成功")){
            log.info("[{}]签到成功", cpdailyUser.getUsername());
        }else {
            log.error("[{}]签到失败：{}", cpdailyUser.getUsername(), resultLines);
        }
        return new AsyncResult<>("任务执行完毕");
    }

}
