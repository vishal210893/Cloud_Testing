package com.solum.cloud;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;

@SpringBootApplication
public class CloudTestingApplication implements CommandLineRunner {

    private static boolean b = false;

    public static void main(String[] args) {
        SpringApplication.run(CloudTestingApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (b) {
            IOUtils.setByteArrayMaxOverride(Integer.MAX_VALUE);
            FileInputStream file = new FileInputStream(new File("C:\\Users\\SolumTravel\\Downloads\\500000_Records_Datanew.xlsx"));
            System.out.println(Runtime.getRuntime().totalMemory() + "    ->    " + Runtime.getRuntime().freeMemory());
            Workbook workbook = StreamingReader.builder()
                    .rowCacheSize(100)    // number of rows to keep in memory (defaults to 10)
                    .bufferSize(4096)     // buffer size to use when reading InputStream to file (defaults to 1024)
                    .open(file);            // InputStream or File for XLSX file (required)
            System.out.println(Runtime.getRuntime().totalMemory() + "    ->    " + Runtime.getRuntime().freeMemory());
            for (Sheet sheet : workbook) {
                int i = 0;
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        printCellValue(cell);
                    }
                    i++;
                }
            }
        }
    }

    public static void printCellValue(Cell cell) {
        CellType cellType = cell.getCellType().equals(CellType.FORMULA)
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (cellType.equals(CellType.STRING)) {
            System.out.print(cell.getStringCellValue() + " | ");
        }
        if (cellType.equals(CellType.NUMERIC)) {
            if (DateUtil.isCellDateFormatted(cell)) {
                System.out.print(cell.getDateCellValue() + " | ");
            } else {
                System.out.print(cell.getNumericCellValue() + " | ");
            }
        }
        if (cellType.equals(CellType.BOOLEAN)) {
            System.out.print(cell.getBooleanCellValue() + " | ");
        }
    }
}
