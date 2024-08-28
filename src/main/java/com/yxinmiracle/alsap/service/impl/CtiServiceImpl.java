package com.yxinmiracle.alsap.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxinmiracle.alsap.mapper.CtiChunkMapper;
import com.yxinmiracle.alsap.mapper.CtiMapper;
import com.yxinmiracle.alsap.mapper.RelationMapper;
import com.yxinmiracle.alsap.model.dto.cti.CtiQueryRequest;
import com.yxinmiracle.alsap.model.dto.cti.PreventEntityQuery;
import com.yxinmiracle.alsap.model.entity.Cti;
import com.yxinmiracle.alsap.model.entity.CtiChunk;
import com.yxinmiracle.alsap.model.entity.Item;
import com.yxinmiracle.alsap.model.entity.Relation;
import com.yxinmiracle.alsap.model.enums.ItemTypeEnum;
import com.yxinmiracle.alsap.model.vo.PostVO;
import com.yxinmiracle.alsap.model.vo.cti.CtiVo;
import com.yxinmiracle.alsap.service.CtiService;
import com.yxinmiracle.alsap.service.ItemService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
@Service
public class CtiServiceImpl extends ServiceImpl<CtiMapper, Cti>
    implements CtiService{

    @Resource
    private CtiMapper ctiMapper;

    @Resource
    private CtiChunkMapper ctiChunkMapper;

    @Resource
    private RelationMapper relationMapper;

    @Override
    public LambdaUpdateWrapper<Cti> getQueryWrapper(CtiQueryRequest ctiQueryRequest) {
        LambdaUpdateWrapper<Cti> ctiQueryWrapper = new LambdaUpdateWrapper<>();
        if (ctiQueryRequest == null){
            return ctiQueryWrapper;
        }
        String title = ctiQueryRequest.getTitle();
        Long userId = ctiQueryRequest.getUserId();
        Date createTime = ctiQueryRequest.getCreateTime();
        Date updateTime = ctiQueryRequest.getUpdateTime();

        // 拼接查询条件
        if (StringUtils.isNotBlank(title)) {
            ctiQueryWrapper.like(StringUtils.isNotBlank(title),Cti::getTitle,title);
        }
        ctiQueryWrapper.orderByDesc(Cti::getUpdateTime);
        ctiQueryWrapper.eq(ObjectUtils.isNotEmpty(userId), Cti::getUserId, userId);
        ctiQueryWrapper.ge(ObjectUtils.isNotEmpty(createTime), Cti::getCreateTime, createTime);
        ctiQueryWrapper.ge(ObjectUtils.isNotEmpty(updateTime), Cti::getUpdateTime, updateTime);

        return ctiQueryWrapper;
    }

    @Override
    public List<Relation> getPreventGraphRelationDataList(PreventEntityQuery preventEntityQuery) {
        List<Relation> relationList = ctiMapper.preventEntitySearch(preventEntityQuery);
        return relationList;
    }

    @Override
    public Page<CtiVo> getCtiVOPage(Page<Cti> ctiPage, Map<Long, Item> itemId2ItemMap, HttpServletRequest request) {
        Page<CtiVo> CtiVoPage = new Page<>(ctiPage.getCurrent(), ctiPage.getSize(), ctiPage.getTotal());
        if (CollUtil.isEmpty(ctiPage.getRecords())) {
            return CtiVoPage;
        }

        List<CtiVo> ctiVoList = ctiPage.getRecords().stream().map(cti -> {
            CtiVo ctiVo = new CtiVo();
            BeanUtils.copyProperties(cti, ctiVo);

            // 获取到的是所有的关于这个CtiI对的itemId表
            List<Long> ctiItemIdList = ctiChunkMapper.selectList(new LambdaQueryWrapper<CtiChunk>()
                            .eq(CtiChunk::getCtiId, cti.getId()))
                    .stream()
                    .map(CtiChunk::getItemId).collect(Collectors.toList());

            ctiVo.setHasGraph(relationMapper.selectCount(new LambdaQueryWrapper<Relation>().eq(Relation::getCtiId, cti.getId())) > 0 ? 1 : 0);

            // 判断它对应的item是不是空的，很有可能是空的
            long sdoCount = 0L;
            long scoCount = 0L;
            if (!ctiItemIdList.isEmpty()) {
                sdoCount = ctiItemIdList.stream()
                        .filter(itemId -> itemId2ItemMap.get(itemId).getItemType() == ItemTypeEnum.SDO.getValue())
                        .count();
                scoCount = ctiItemIdList.stream()
                        .filter(itemId -> itemId2ItemMap.get(itemId).getItemType() == ItemTypeEnum.SCO.getValue())
                        .count();
            }
            ctiVo.setEntityNum((int) (scoCount + sdoCount));
            ctiVo.setSdoNum((int) sdoCount);
            ctiVo.setScoNum((int) scoCount);
            return ctiVo;
        }).collect(Collectors.toList());
        CtiVoPage.setRecords(ctiVoList);
        return CtiVoPage;
    }

}




