package com.hex.bigdata.udsp.service;

import com.hex.bigdata.udsp.common.util.UdspCommonUtil;
import com.hex.bigdata.udsp.im.constant.RealtimeStatus;
import com.hex.bigdata.udsp.im.provider.impl.model.modeling.MqModel;
import com.hex.bigdata.udsp.model.HeartbeatInfo;
import com.hex.bigdata.udsp.model.RealtimeNodeInfo;
import com.hex.bigdata.udsp.model.RealtimeTotalInfo;
import com.hex.bigdata.udsp.quartz.QuartzManager;
import com.hex.bigdata.udsp.quartz.RealtimeJob;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by JunjieM on 2017-9-15.
 */
@Service
public class RealtimeService {
    private static Logger logger = LogManager.getLogger(RealtimeService.class);
    private static final FastDateFormat format = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String HOST_KEY = UdspCommonUtil.getLocalIpFromInetAddress();

    /**
     * 异常或停止的作业信息踢除阀值(秒)
     */
    @Value("${realtime.jobinfo.timeout:86400}")
    private Long realtimeJobinfoTimeout;

    @Autowired
    private RealtimeTotalService realtimeTotalService;
    @Autowired
    private RealtimeNodeService realtimeNodeService;
    @Autowired
    private HeartbeatService heartbeatService;
    @Autowired
    @Qualifier("quartzManager")
    private QuartzManager quartzManager;

    /**
     * 检查实时任务状态
     */
    public void checkRealtimeStatus() {
        logger.debug(new Date() + ": Check Realtime Status Job doing something...");
//        if (!quartzManager.checkTriggerExists("test", "test") && !quartzManager.checkJobExists("test", "test")) {
//            quartzManager.addJob("test", "test", "test", "test", JobTest.class, "0/1 * * * * ?");
//        }
//        if (!quartzManager.checkTriggerExists("test2", "test2") && !quartzManager.checkJobExists("test2", "test2")) {
//            quartzManager.addJob("test2", "test2", "test2", "test2", JobTest2.class, "0/2 * * * * ?");
//        }
        List<RealtimeTotalInfo> realtimeTotalInfos = realtimeTotalService.selectList();
        logger.debug("实时作业信息个数：" + realtimeTotalInfos.size());
        for (RealtimeTotalInfo totalInfo : realtimeTotalInfos) {
            MqModel model = totalInfo.getModel();
            String id = model.getId();
            String consumerCronExpression = model.getConsumerCronExpression();
            RealtimeStatus status = totalInfo.getStatus();
            String startHost = totalInfo.getStartHost();
            String stopHost = totalInfo.getStopHost();
            String updateTime = totalInfo.getUpdateTime();
            if (((RealtimeStatus.START_FAIL == status || RealtimeStatus.RUN_FAIL == status) && HOST_KEY.equals(startHost))
                    || ((RealtimeStatus.STOP_FAIL == status || RealtimeStatus.STOP_SUCCESS == status) && HOST_KEY.equals(stopHost))
                    ) { // 异常或停止
                // --------------------------------------------异常或停止处理---------------------------------------------
                logger.debug("异常或停止处理...");
                // 管理节点操作
                try {
                    if (System.currentTimeMillis() - realtimeJobinfoTimeout * 1000 >= format.parse(updateTime).getTime()) {
                        realtimeNodeService.deleteList(id);
                        realtimeTotalService.delete(id);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } else if (RealtimeStatus.READY_START == status) { // 准备启动
                // --------------------------------------------开始启动作业---------------------------------------------
                logger.debug("开始启动作业...");
                // 管理节点操作
                if (HOST_KEY.equals(startHost)) {
                    realtimeTotalService.starting(id);
                }
                // 所有节点操作
                realtimeNodeService.starting(id);
                try {
                    if (!quartzManager.checkTriggerExists(id, id) && !quartzManager.checkJobExists(id, id)) {
                        quartzManager.addJob(id, id, id, id, RealtimeJob.class, consumerCronExpression);
                    }
                    realtimeNodeService.running(id);
                } catch (Exception e) {
                    realtimeNodeService.startFail(id, e.getMessage());
                }
            } else if (RealtimeStatus.STARTING == status) { // 开始启动
                // --------------------------------------------开始检查作业启动情况---------------------------------------------
                logger.debug("开始检查作业启动情况...");
                // 管理节点操作
                if (HOST_KEY.equals(startHost)) {
                    List<RealtimeNodeInfo> realtimeNodeInfos = realtimeNodeService.selectList(id);
                    for (RealtimeNodeInfo nodeInfo : realtimeNodeInfos) {
                        if (RealtimeStatus.RUNNING == nodeInfo.getStatus()) {
                            realtimeTotalService.running(id);
                            break;
                        }
                    }
                }
            } else if (RealtimeStatus.RUNNING == status) { // 正在运行
                // --------------------------------------------开始检查作业运行情况---------------------------------------------
                logger.debug("开始检查作业运行情况...");
                // 管理节点操作
                if (HOST_KEY.equals(startHost)) {
                    boolean flg = true;
                    List<RealtimeNodeInfo> realtimeNodeInfos = realtimeNodeService.selectList(id);
                    for (RealtimeNodeInfo nodeInfo : realtimeNodeInfos) {
                        if (RealtimeStatus.RUNNING == nodeInfo.getStatus()) {
                            flg = false;
                            break;
                        }
                    }
                    if (flg) realtimeTotalService.runFail(id);
                }
                // 所有节点操作
                RealtimeNodeInfo nodeInfo = realtimeNodeService.select(id);
                if (RealtimeStatus.RUNNING == nodeInfo.getStatus()
                        && !quartzManager.checkTriggerExists(id, id) && !quartzManager.checkJobExists(id, id)) {
                    realtimeNodeService.runFail(id, "检查任务和触发器发现都不存在");
                }
            } else if (RealtimeStatus.READY_STOP == status) { // 准备停止
                // --------------------------------------------开始停止作业---------------------------------------------
                logger.debug("开始停止作业...");
                // 管理节点操作
                if (HOST_KEY.equals(stopHost)) {
                    realtimeTotalService.stoping(id);
                }
                // 所有节点操作
                realtimeNodeService.stoping(id);
                try {
                    quartzManager.removeJob(id, id, id, id);
                    realtimeNodeService.stopSuccess(id);
                } catch (Exception e) {
                    if (quartzManager.checkTriggerExists(id, id) && quartzManager.checkJobExists(id, id)) {
                        realtimeNodeService.running(id);
                    } else if (!quartzManager.checkTriggerExists(id, id) && !quartzManager.checkJobExists(id, id)) {
                        realtimeNodeService.stopSuccess(id);
                    } else {
                        realtimeNodeService.stopFail(id, e.getMessage());
                    }
                }
            } else if (RealtimeStatus.STOPING == status) { // 开始停止
                // --------------------------------------------开始检查作业停止情况---------------------------------------------
                logger.debug("开始检查作业停止情况...");
                // 管理节点操作
                if (HOST_KEY.equals(stopHost)) {
                    List<RealtimeNodeInfo> realtimeNodeInfos = realtimeNodeService.selectList(id);
                    int stopSuccessCount = 0;
                    int stopFailCount = 0;
                    int runningCount = 0;
                    for (RealtimeNodeInfo nodeInfo : realtimeNodeInfos) {
                        if (RealtimeStatus.STOP_SUCCESS == nodeInfo.getStatus()) {
                            stopSuccessCount++;
                        } else if (RealtimeStatus.STOP_FAIL == nodeInfo.getStatus()) {
                            stopFailCount++;
                        } else if (RealtimeStatus.RUNNING == nodeInfo.getStatus()) {
                            runningCount++;
                        }
                    }
                    if (stopSuccessCount == realtimeNodeInfos.size()) {
                        realtimeTotalService.stopSuccess(id);
                    } else if (stopFailCount > 0 && runningCount == 0) {
                        realtimeTotalService.stopFail(id);
                    }
                }
            }
        }
    }

    /**
     * 检查每个节点实时任务情况
     */
    public void checkRealtimeNodes() {
        List<HeartbeatInfo> heartbeatInfos = heartbeatService.selectList();
        List<String> list = new ArrayList<>();
        for (HeartbeatInfo info : heartbeatInfos) {
            list.add(info.getIp());
        }
        List<RealtimeTotalInfo> realtimeTotalInfos = realtimeTotalService.selectList();
        for (RealtimeTotalInfo totalInfo : realtimeTotalInfos) {
            MqModel model = totalInfo.getModel();
            String id = model.getId();
            List<RealtimeNodeInfo> realtimeNodeInfos = realtimeNodeService.selectList(id);
            for (RealtimeNodeInfo info : realtimeNodeInfos) {
                String host = info.getHost();
                if (!list.contains(host)) {
                    realtimeNodeService.delete(id, host);
                }
            }
        }
    }
}
