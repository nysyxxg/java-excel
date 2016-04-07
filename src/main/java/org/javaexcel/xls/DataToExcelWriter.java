package org.javaexcel.xls;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.javaexcel.ExcelWriter;
import org.javaexcel.model.ExcelMetaData;
import org.javaexcel.model.ExcelTitle;
import org.javaexcel.util.JsonUtil;

/*
 * File name   : ExcelWriterImpl.java
 * @Copyright  : luoyoub@163.com
 * Description : javaexcel
 * Author      : Robert
 * CreateTime  : 2016年4月6日
 */
public class DataToExcelWriter extends ExcelWriter {
    private List<ExcelTitle> bigheaders = new ArrayList<ExcelTitle>();
    private ExcelMetaData metedata;
    private List<Object> allDatas;
    private int rownum = 0;
    // 样式表
    private Map<String, CellStyle> stylesMap = new HashMap<String, CellStyle>();

    @Override
    public boolean process(ExcelMetaData metedata, List<Object> datas,
            String fileName) throws Exception {
        this.metedata = metedata;
        this.allDatas = datas;
        boolean result = false;

        // 校验fileName的父目录是否存在
        File file = new File(fileName);
        if (Files.notExists(Paths.get(file.getParent()))) {
            throw new Exception("Output directory does not exist.");
        }

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            wb = new HSSFWorkbook();
            sheet = wb.createSheet(this.metedata.getSheetName());
            initStyle();

            // 写表头
            writeHeader();

            // 写数据
            writeData();

            wb.write(out);
            wb.close();
            result = true;
        } catch (Exception e) {
            throw e;
        }
        return result;
    }

    private void writeHeader() {
        if (null == metedata.getExcelTitle() || metedata.getExcelTitle().isEmpty()) {
            return;
        }

        if (null != this.metedata.getExcelTitle() && !this.metedata.getExcelTitle().isEmpty()) {
            cellStyle = this.stylesMap.get("titleStyle");
            List<ExcelTitle> titles = metedata.getExcelTitle();
            CellRangeAddress address = null;
            for (ExcelTitle ex : titles) {
                if (null != ex.getSubTitles() && ex.getSubTitles().size() >= 2) {
                    address = new CellRangeAddress(0, 0, ex.getSubTitles().get(0).getIndex(), ex.getSubTitles().get(ex.getSubTitles().size() - 1).getIndex());
                    sheet.addMergedRegion(address);

                    bigheaders.add(ex);
                    continue;
                }

                // 合并头两行的某一列
                address = new CellRangeAddress(0, 1, ex.getIndex(), ex.getIndex());
                sheet.addMergedRegion(address);
            }

            // 写Excel表头
            Row oneRow = sheet.createRow(rownum++);
            Row twoRow = sheet.createRow(rownum++);
            for (ExcelTitle ete : titles) {
                if (null != ete.getSubTitles() && ete.getSubTitles().size() >= 2) {
                    cell = oneRow.createCell(ete.getSubTitles().get(0).getIndex());
                    cell.setCellValue(ete.getDisplayName());
                    cell.setCellStyle(cellStyle);

                    for (ExcelTitle subTitle : ete.getSubTitles()) {
                        cell = twoRow.createCell(subTitle.getIndex());
                        cell.setCellValue(subTitle.getDisplayName());
                        cell.setCellStyle(cellStyle);

                        allTitles.add(subTitle);
                    }
                } else {
                    sheet.setColumnWidth(ete.getIndex(), ete.getDisplayName().length() * 4 * 256);
                    cell = oneRow.createCell(ete.getIndex());
                    cell.setCellValue(ete.getDisplayName());
                    cell.setCellStyle(cellStyle);

                    allTitles.add(ete);
                }
            }
        } else {
            allTitles.addAll(this.metedata.getExcelTitle());
            Row rowHeader = sheet.createRow(rownum++);
            for (ExcelTitle eh : this.metedata.getExcelTitle()) {
                int index = eh.getIndex();
                cell = rowHeader.createCell(index);
                cell.setCellValue(eh.getName());
                cell.setCellStyle(cellStyle);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void writeData() {
        if (null == this.allDatas || this.allDatas.isEmpty()) {
            return;
        }

        for (Object object : allDatas) {
            Map<String, Object> dataMap = JsonUtil.stringToBean(JsonUtil.beanToString(object), Map.class);
            if (null == dataMap || dataMap.isEmpty()) {
                continue;
            }

            int rowsize = getColumns(dataMap);
            int maxRow = rownum + rowsize - 1;
            if (rowsize > 0) {
                // 需要处理行的数据合并
                allTitles.stream().filter(et -> et.isMerge()).forEach(ex -> {
                    CellRangeAddress address = new CellRangeAddress(rownum, maxRow, ex.getIndex(), ex.getIndex());
                    sheet.addMergedRegion(address);
                });

                row = sheet.createRow(rownum++);
                allTitles.forEach(eh -> {
                    cell = row.createCell(eh.getIndex());
                    cell.setCellValue(dataMap.get(eh.getName()) + "");
                });

                for (int i = 0; i < rowsize; i++) {
                    for (ExcelTitle eh : bigheaders) {
                        Object obj = dataMap.get(eh.getName());
                        if (obj instanceof List) {
                            Map<String, Object> detailData = (Map<String, Object>) ((List) obj).get(i);

                            eh.getSubTitles().stream().forEach(exte -> {
                                cell = row.createCell(exte.getIndex());
                                cell.setCellValue(detailData.get(exte.getName()) + "");
                            });
                        }
                    }

                    // 最后一次循环需要处理迭代索引
                    if (i != rowsize - 1) {
                        row = sheet.createRow(rownum++);
                    }
                }
            } else {
                row = sheet.createRow(rownum++);
                // row.setHeight((short) 0x249);
                createCell(row, dataMap);
            }
        }
    }

    private void createCell(Row row, Map<String, Object> data) {
        allTitles.stream().forEach(eh -> {
            Integer index = eh.getIndex();
            cell = row.createCell(index);
            cell.setCellValue(data.get(eh.getName()) + "");
        });
    }

    @SuppressWarnings("rawtypes")
    private static int getColumns(Map<String, Object> dataMap) {
        for (Object obj : dataMap.values()) {
            if (obj instanceof List) {
                return ((List) obj).size();
            }
        }
        return 0;
    }
}