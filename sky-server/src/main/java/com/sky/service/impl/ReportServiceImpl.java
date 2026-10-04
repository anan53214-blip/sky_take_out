package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;
    /**
     * 获取指定时间区间的营业额
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> list=new ArrayList<>();
        while (!begin.equals(end)){
            list.add(begin);
            begin=begin.plusDays(1);
        }

        List<Double> turnoverList=new ArrayList<>();
        for (LocalDate localDate : list) {
            //查询date日期对应的营业额
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);
            //查数据库
            Map map=new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover ==null?0.0:turnover;
            turnoverList.add(turnover);
        }

        //封装VO对象
        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(list,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    /**
     * 获取指定时间区间的用户数据
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> list=new ArrayList<>();
        while (!begin.equals(end)){
            list.add(begin);
            begin=begin.plusDays(1);
        }

        List<Integer> newUserList=new ArrayList<>();
        List<Integer> totalUserList=new ArrayList<>();

        for (LocalDate localDate : list) {
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);
            Map map=new HashMap();
            map.put("end",endTime);
            Integer totalUser = userMapper.countByMap(map);
            map.put("begin",beginTime);
            Integer newUser = userMapper.countByMap(map);
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(list,","))
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();
    }

    /**
     * 获取指定时间区间的订单数据
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> list=new ArrayList<>();
        while (!begin.equals(end)){
            list.add(begin);
            begin=begin.plusDays(1);
        }
        List<Integer> orderCountList =new ArrayList<>();
        List<Integer> validCountList =new ArrayList<>();
        for (LocalDate localDate : list) {
            //查询每日订单总数
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            //查询每天的有效订单数
            Integer validCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            orderCountList.add(orderCount);
            validCountList.add(validCount);
        }

        //订单总数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //有效订单总数
        Integer totalValidCount = validCountList.stream().reduce(Integer::sum).get();
        Double orderCompletionRate = 0.0;
        if(totalOrderCount!=0)
        {orderCompletionRate = totalValidCount.doubleValue() / totalOrderCount;}
        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(list,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(totalValidCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 获取指定时间区间的销量top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getTop10Statistics(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop(beginTime, endTime);
        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        return SalesTop10ReportVO
                .builder()
                .nameList(StringUtils.join(nameList,","))
                .numberList(StringUtils.join(numberList,","))
                .build();
    }

    /**
     * 导出报表数据
     * @param response
     */
    public void exportBusinessDate(HttpServletResponse response) {
        //查询数据库获得近30天的营业数据
        LocalDate beginDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now().minusDays(1);

        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(beginDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));

        //通过POI将数据写入excel文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");

        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);

            XSSFSheet sheet = excel.getSheet("Sheet1");
            sheet.getRow(1).getCell(1).setCellValue("时间："+beginDate+"至"+endDate);

            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());

            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());

            for (int i=0;i<30;i++){
                LocalDate localDate=beginDate.plusDays(i);
                BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(localDate, LocalTime.MIN), LocalDateTime.of(localDate, LocalTime.MAX));

                row = sheet.getRow(7+i);
                row.getCell(1).setCellValue(localDate.toString());
                row.getCell(2).setCellValue(businessDataVO.getTurnover());
                row.getCell(3).setCellValue(businessDataVO.getValidOrderCount());
                row.getCell(3).setCellValue(businessDataVO.getOrderCompletionRate());
                row.getCell(3).setCellValue(businessDataVO.getUnitPrice());
                row.getCell(3).setCellValue(businessDataVO.getNewUsers());
            }

            ServletOutputStream out= response.getOutputStream();
            excel.write(out);

            out.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map=new HashMap();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);
        Integer count = orderMapper.countByMap(map);
        return count==null?0:count;
    }
}
