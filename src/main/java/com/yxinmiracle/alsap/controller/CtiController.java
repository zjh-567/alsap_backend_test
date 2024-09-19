package com.yxinmiracle.alsap.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.yxinmiracle.alsap.aiService.AiServer;
import com.yxinmiracle.alsap.annotation.AuthCheck;
import com.yxinmiracle.alsap.annotation.DecryptRequestBody;
import com.yxinmiracle.alsap.common.BaseResponse;
import com.yxinmiracle.alsap.common.ErrorCode;
import com.yxinmiracle.alsap.common.ResultUtils;
import com.yxinmiracle.alsap.constant.UserConstant;
import com.yxinmiracle.alsap.exception.BusinessException;
import com.yxinmiracle.alsap.exception.ThrowUtils;
import com.yxinmiracle.alsap.model.dto.cti.*;
import com.yxinmiracle.alsap.model.entity.*;
import com.yxinmiracle.alsap.model.model.ModelResult;
import com.yxinmiracle.alsap.model.vo.cti.CtiDetailVo;
import com.yxinmiracle.alsap.model.vo.cti.CtiVo;
import com.yxinmiracle.alsap.model.vo.cti.CtiWordLabelVo;
import com.yxinmiracle.alsap.model.vo.user.NoRoleUserVo;
import com.yxinmiracle.alsap.service.*;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * cti接口
 */
@RestController
@RequestMapping("/cti")
@Slf4j
public class CtiController {

    @Resource
    private CtiService ctiService;

    @Resource
    private ItemService itemService;

    @Resource
    private CtiChunkService ctiChunkService;

    @Resource
    private EntityService entityService;

    @Resource
    private RelationService relationService;

    @Resource
    private AiServer aiServer;

    @Resource
    private UserService userService;

    // region 增删改查

    /**
     * 根据请求参数来查询数据
     *
     * @param ctiQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @ApiOperation(value = "根据Cti查询信息返回CtiVo信息")
    @DecryptRequestBody
    public BaseResponse<Page<CtiVo>> getCtiByPage(@RequestBody CtiQueryRequest ctiQueryRequest, HttpServletRequest request) {
        // 获取当前页数以及对应的请求大小
        long current = ctiQueryRequest.getCurrent();
        long size = ctiQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 先进行分页查询，找到分页后的cti信息
        Page<Cti> ctiPage = ctiService.page(new Page<>(current, size), ctiService.getQueryWrapper(ctiQueryRequest));

        // 获取item的信息
        Map<Long, Item> itemId2ItemMap = itemService.getItemId2ItemMap();

        return ResultUtils.success(ctiService.getCtiVOPage(ctiPage, itemId2ItemMap, request));
    }

    /**
     * 添加Cti情报接口
     *
     * @param ctiAddRequest
     * @param request
     * @return 返回的是CTI情报的ID
     */
    @PostMapping("/add")
    @ApiOperation("添加Cti情报")
    @DecryptRequestBody
    @Transactional
    public BaseResponse<Long> addCtiReport(@RequestBody CtiAddRequest ctiAddRequest, HttpServletRequest request) {
        if (ctiAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Cti cti = new Cti();
        BeanUtils.copyProperties(ctiAddRequest, cti);


        // 请求AI服务获取对应的数据
//        ModelResult modelResult = Optional.ofNullable(aiServer.getCtiExtractorEntityAndRelationAns(ctiAddRequest.getContent()))
//                .orElseThrow(() -> new BusinessException(ErrorCode.AI_SERVER_ERROR));
//        cti.setWordList(JSONUtil.toJsonStr(modelResult.getWordList()));
//        cti.setLabelList(JSONUtil.toJsonStr(modelResult.getLabelList()));
//
//        boolean result = ctiService.save(cti);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 调用异步方法
//        CompletableFuture.runAsync(() -> {
//            judgeService.doJudge(questionSubmitId);
//        });

        return ResultUtils.success(cti.getId());
    }


    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @ApiOperation(value = "根据CtiId删除对应的Cti，需要管理员权限")
    @DecryptRequestBody
    @Transactional
    public BaseResponse<Long> deleteCtiByCtiId(@RequestBody CtiDeleteRequest ctiDeleteRequest, HttpServletRequest request) {
        if (ObjectUtils.isEmpty(ctiDeleteRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Cti cti = new Cti();
        BeanUtils.copyProperties(ctiDeleteRequest, cti);
        boolean result = ctiService.removeById(cti);
        boolean ctiChunkResult = ctiChunkService.remove(new LambdaQueryWrapper<CtiChunk>().eq(CtiChunk::getCtiId, cti.getId()));
        boolean entityResult = entityService.remove(new LambdaQueryWrapper<Entity>().eq(Entity::getCtiId, cti.getId()));
        boolean relationResult = relationService.remove(new LambdaQueryWrapper<com.yxinmiracle.alsap.model.entity.Relation>().eq(Relation::getCtiId, cti.getId()));
        ThrowUtils.throwIf(!(result && ctiChunkResult && entityResult && relationResult), ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(cti.getId());
    }

    @PostMapping("/word/label/page/")
    @DecryptRequestBody
    @ApiOperation(value = "获取模型已经标注好的数据")
    public BaseResponse<CtiWordLabelVo> getPageCtiWordLabelList(@RequestBody CtiModelWordLabelResultDto ctiModelWordLabelResultDto, HttpServletRequest request) {
        if (ObjectUtils.isEmpty(ctiModelWordLabelResultDto)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (ctiModelWordLabelResultDto.getCurrent() <= 0 || ctiModelWordLabelResultDto.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long ctiId = ctiModelWordLabelResultDto.getCtiId();
        Integer current = ctiModelWordLabelResultDto.getCurrent();
        Integer size = ctiModelWordLabelResultDto.getSize();

        Cti cti = ctiService.getById(ctiId);
        if (ObjectUtils.isEmpty(cti.getWordList())) {
            throw new BusinessException(ErrorCode.EMPTY_DATA);
        }

        CtiWordLabelVo ctiWordLabelVo = new CtiWordLabelVo();

        Gson gson = new Gson();
        Type type = new TypeToken<List<List<String>>>() {}.getType();
        List<List<String>> wordList = gson.fromJson(cti.getWordList(), type);
        List<List<String>> labelList = gson.fromJson(cti.getLabelList(), type);

        int dataSize = wordList.size();
        int start = (current - 1) * size;
        int end = current * size;
        ThrowUtils.throwIf(start > dataSize, ErrorCode.PARAMS_ERROR);
        end = Math.min(end, dataSize);
        ArrayList<List<String>> pageWordList = new ArrayList<>(wordList.subList(start, end));
        ArrayList<List<String>> pageLabelList = new ArrayList<>(labelList.subList(start, end));
        ctiWordLabelVo.setWordList(pageWordList);
        ctiWordLabelVo.setLabelList(pageLabelList);
        ctiWordLabelVo.setTotal(dataSize);
        return ResultUtils.success(ctiWordLabelVo);
    }


    /**
     * cti详情界面的数据返回，这里是包括实体的
     *
     * @param ctiDetailQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/detail")
    @DecryptRequestBody
    @ApiOperation(value = "根据ctiId获取对应的cti详情信息")
    public BaseResponse<CtiDetailVo> getDetailCti(@RequestBody CtiDetailQueryRequest ctiDetailQueryRequest, HttpServletRequest request) {
        Long ctiId = ctiDetailQueryRequest.getId();
        if (ObjectUtils.isEmpty(ctiId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        Cti cti = ctiService.getById(ctiId);
        CtiDetailVo ctiDetailVo = new CtiDetailVo();
        BeanUtils.copyProperties(cti, ctiDetailVo);
        List<CtiChunk> ctiChunkList = ctiChunkService.list(new LambdaQueryWrapper<CtiChunk>().eq(CtiChunk::getCtiId, cti.getId()));
        ctiDetailVo.setCtiChunkList(ctiChunkList);
        ctiDetailVo.setEntityNum(ctiChunkList.size());

        // 获取sco和sdo的信息
        Map<Long, Item> itemId2ItemMap = itemService.getItemId2ItemMap();
        CtiVo scoAndSdoCount = ctiService.getScoAndSdoCountByCtiId(ctiId, itemId2ItemMap);
        ctiDetailVo.setScoNum(scoAndSdoCount.getScoNum());
        ctiDetailVo.setSdoNum(scoAndSdoCount.getSdoNum());

        // 获取创建该情报的用户
        Long userId = cti.getUserId();
        User user = userService.getById(userId);
        ThrowUtils.throwIf(ObjectUtils.isEmpty(user), ErrorCode.NOT_FOUND_ERROR);
        NoRoleUserVo noRoleUserVo = new NoRoleUserVo();
        BeanUtils.copyProperties(user, noRoleUserVo);
        ctiDetailVo.setUser(noRoleUserVo);

        return ResultUtils.success(ctiDetailVo);
    }


}
