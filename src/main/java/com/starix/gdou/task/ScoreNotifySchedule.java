package com.starix.gdou.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 2020.08.07 关闭成绩更新检测
 * @author Starix
 * @date 2020-07-18 12:14
 */
@Slf4j
//@Component
public class ScoreNotifySchedule {

    @Autowired
    private ScoreNotifyAsyncTask scoreNotifyAsyncTask;

    // 定时检测成绩是否更新，更新则发送邮件通知
    // @Scheduled(cron = "0 0 8,20 * * ?")
    public void signIntask() throws Exception {
        log.info("==============开始执行成绩更新检测任务==============");
        scoreNotifyAsyncTask.checkScoreUpdateAndNotify();
    }

}
