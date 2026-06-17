package com.kaer.service.impl;

import com.kaer.service.DocumentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 文档存储服务实现类
 * 负责文档的上传、存储和路径管理，采用分层目录结构存储文件
 * 路径格式: basePath/kbId/documentId/uniqueFileName
 */
@Service
@Slf4j
public class DocumentStorageServiceImpl implements DocumentStorageService {

    /**
     * 文件存储基础路径
     * 可通过配置文件 document.storage.base-path 指定，默认值为 ./data/documents
     */
    @Value("${document.storage.base-path:./data/documents}")
    private String basePath;

    /**
     * 保存上传的文档文件
     * 将文件存储到指定知识库下的文档目录，并返回相对路径
     *
     * @param kbId       知识库ID，用于区分不同知识库的文件
     * @param documentId 文档ID，用于标识单个文档
     * @param file       上传的文件对象
     * @return 文件保存后的相对路径（格式：kbId/documentId/uniqueFileName）
     * @throws FileNotFoundException 当上传文件为空时抛出
     * @throws IOException           当文件保存失败时抛出
     */
    @Override
    public String saveFile(String kbId, String documentId, MultipartFile file) throws IOException {
        // 校验文件是否为空
        if (file.isEmpty()) {
            throw new FileNotFoundException("上传文件为空");
        }

        // 构建文件存储路径: basePath/kbId/documentId/filename
        // 将基础路径与知识库ID拼接，形成知识库专属目录路径
        Path kbDir = Paths.get(basePath, kbId);
        // 在知识库目录下追加文档ID，形成文档专属目录路径
        Path documentDir = kbDir.resolve(documentId);
        // 递归创建目录（如果不存在），支持多级目录创建
        Files.createDirectories(documentDir);

        // 生成唯一文件名（使用UUID + 原始文件扩展名）
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        // 获取原始文件扩展名（仅当文件名包含点号时）
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 使用UUID确保文件名唯一性，避免文件覆盖冲突
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        // 构建目标文件路径并保存文件
        Path targetPath = documentDir.resolve(uniqueFileName);
        // 复制文件输入流到目标路径，若文件已存在则覆盖
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 构建相对路径（统一使用斜杠作为分隔符，兼容跨平台）
        String relativePath = Paths.get(kbId, documentId, uniqueFileName).toString().replace("\\", "/");
        // 记录文件保存日志，便于追踪和排查问题
        log.info("文件保存成功: kbId={}, documentId={}, filename={}, path={}", kbId, documentId, originalFilename, relativePath);
        return relativePath;
    }

    /**
     * 根据相对路径获取完整的文件路径
     * 将基础路径与相对路径拼接，返回完整的Path对象
     *
     * @param filePath 文件相对路径（格式：kbId/documentId/uniqueFileName）
     * @return 完整的文件路径对象
     */
    @Override
    public Path getFilePath(String filePath) {
        // 将基础路径与相对路径拼接，形成完整路径
        return Paths.get(basePath, filePath);
    }

    /**
     * 检查文件是否存在
     *
     * @param filePath 文件路径
     * @return true表示文件存在，false表示不存在
     */
    @Override
    public boolean fileExists(String filePath) {
        Path path = getFilePath(filePath);
        return Files.exists(path) && Files.isRegularFile(path);

    }

    /**
     * 删除文件及其所在目录（如果目录为空）
     *
     * @param filePath 文件相对路径（格式：kbId/documentId/uniqueFileName）
     * @throws IOException 当文件删除操作失败时抛出
     */
    @Override
    public void deleteFile(String filePath) throws IOException {
        // 获取完整文件路径
        Path fullPath = getFilePath(filePath);

        // 检查文件是否存在
        if (Files.exists(fullPath)) {
            // 删除文件
            Files.delete(fullPath);
            log.info("文件删除成功: {}", fullPath);

            // 获取文件所在的父目录（文档专属目录）
            Path parentDir = fullPath.getParent();
            // 尝试删除空目录
            if (parentDir != null && Files.exists(parentDir)) {
                try {
                    Files.delete(parentDir);
                    log.info("目录删除成功: {}", parentDir);
                } catch (IOException e) {
                    // 目录不为空或其他原因无法删除，忽略此异常
                    // 设计考虑：即使目录删除失败，文件已成功删除，不影响主流程
                    log.debug("目录删除失败（可能不为空）: {}", parentDir);
                }
            }
        } else {
            // 文件不存在，跳过删除
            log.info("文件不存在,跳过删除: {}", fullPath);
        }
    }
}