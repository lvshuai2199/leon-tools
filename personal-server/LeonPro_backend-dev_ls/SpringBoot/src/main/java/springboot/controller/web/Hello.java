package springboot.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import springboot.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class Hello {

    @Autowired
    RedisUtil redisUtil = new RedisUtil();
    @RequestMapping("hello")
    public String Hello(){
        redisUtil.save("lv","leon");

        redisUtil.get("lv");

        return "hello world";
    }


    private static final String UPLOAD_DIR = "uploads/";

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "请选择一个文件上传";
        }

        try {
            // 获取文件名
            String fileName = file.getOriginalFilename();

            // 确保目录存在
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 保存文件
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            return "文件上传成功: " + fileName;
        } catch (IOException e) {
            e.printStackTrace();
            return "文件上传失败: " + e.getMessage();
        }
    }

    @PostMapping("/upload-multiple")
    public String uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        StringBuilder fileNames = new StringBuilder();
        for (MultipartFile file : files) {
            Path fileNameAndPath = Paths.get(UPLOAD_DIR, file.getOriginalFilename());
            fileNames.append(file.getOriginalFilename()).append(" ");
            try {
                Files.write(fileNameAndPath, file.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
                return "文件上传失败: " + e.getMessage();
            }
        }
        return "多文件上传成功: " + fileNames;
    }



}

