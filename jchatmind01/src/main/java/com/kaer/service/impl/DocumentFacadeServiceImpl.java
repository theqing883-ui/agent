package com.kaer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kaer.converter.DocumentConverter;
import com.kaer.exception.BizException;
import com.kaer.mapper.ChunkBgeM3Mapper;
import com.kaer.mapper.DocumentMapper;
import com.kaer.model.dto.DocumentDTO;
import com.kaer.model.entity.ChunkBgeM3;
import com.kaer.model.entity.Document;
import com.kaer.model.request.CreateDocumentRequest;
import com.kaer.model.request.UpdateDocumentRequest;
import com.kaer.model.response.CreateDocumentResponse;
import com.kaer.model.response.GetDocumentsResponse;
import com.kaer.model.vo.DocumentVO;
import com.kaer.service.DocumentFacadeService;
import com.kaer.service.DocumentStorageService;
import com.kaer.service.MarkdownParserService;
import com.kaer.service.RagService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class DocumentFacadeServiceImpl implements DocumentFacadeService {
    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final RagService ragService;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;


    /**
     * 上传文档到知识库
     * 流程：校验文件 -> 创建数据库记录 -> 保存文件 -> 更新文件路径 -> 处理Markdown文件（可选）
     *
     * @param kbId 知识库ID
     * @param file 上传的文件对象
     * @return 创建文档响应，包含文档ID
     * @throws BizException     当文件为空或数据库操作失败时抛出
     * @throws RuntimeException 当处理过程中发生异常时抛出
     */
    @Override
    public CreateDocumentResponse uploadDocument(String kbId, MultipartFile file) {
        try {
            // 1. 校验文件是否为空
            if (file.isEmpty()) {
                throw new BizException("上传文件为空");
            }

            // 2. 提取文件信息
            String originalFilename = file.getOriginalFilename();
            String filetype = getFileType(originalFilename);
            long size = file.getSize();

            // 3. 创建文档记录（先创建记录，获取数据库自动生成的 documentId）
            DocumentDTO documentDTO = DocumentDTO.builder().kbId(kbId).filename(originalFilename).filetype(filetype).size(size).build();

            Document document = documentConverter.toEntity(documentDTO);
            LocalDateTime now = LocalDateTime.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);

            // 4. 插入数据库，ID由数据库自动生成
            int result = documentMapper.insert(document);
            if (result <= 0) {
                throw new BizException("文档创建失败");
            }

            // 获取数据库生成的文档ID
            String documentId = document.getId();

            // 5. 保存文件到存储服务
            // 注意：保存文件需要先获取documentId，因为文件路径包含documentId
            String relativePath = documentStorageService.saveFile(kbId, documentId, file);

            // 6. 更新文档记录，保存文件路径到metadata
            DocumentDTO.MetaData metaData = new DocumentDTO.MetaData();
            metaData.setFilePath(relativePath);
            documentDTO.setMetadata(metaData);
            documentDTO.setUpdatedAt(now);
            documentDTO.setCreatedAt(now);

            Document updateDocument = documentConverter.toEntity(documentDTO);
            updateDocument.setId(documentId);
            updateDocument.setUpdatedAt(now);
            updateDocument.setCreatedAt(now);

            documentMapper.updateById(updateDocument);
            log.info("文档上传成功: kbId={}, documentId={}, filename={}", kbId, documentId, originalFilename);

            // 7. 如果是Markdown文件，解析并生成chunks用于RAG检索
            // equalsIgnoreCase: 忽略大小写比较字符串相等性
            // 支持 md、MD、markdown、Markdown 等多种大小写形式的文件类型标识
            if ("md".equalsIgnoreCase(filetype) || "markdown".equalsIgnoreCase(filetype)) {
                processMarkdownFile(kbId, documentId, relativePath);
            } else {
                // TODO: 未来可以增加其他文件类型（如PDF、DOCX）的处理逻辑
                log.warn("待新增处理的文件类型: {}", filetype);
            }

            // 返回创建结果
            return CreateDocumentResponse.builder().documentId(documentId).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 查询所有文档VO
    @Override
    public GetDocumentsResponse getDocuments(String kbId) {
        List<Document> documents = documentMapper.selectAll();
        ArrayList<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO converterVO = documentConverter.toVO(document);
                result.add(converterVO);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetDocumentsResponse.builder().documents(result.toArray(new DocumentVO[0])).build();
    }

    // 根据知识库ID查询文档VO
    @Override
    public GetDocumentsResponse getDocumentsByKbId(String kbId) {
        List<Document> documents = documentMapper.selectByKbId(kbId);
        ArrayList<DocumentVO> result = new ArrayList<>();
        for (Document document : documents) {
            try {
                DocumentVO documentVO = documentConverter.toVO(document);
                result.add(documentVO);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetDocumentsResponse.builder().documents(result.toArray(new DocumentVO[0])).build();
    }

    /**
     * 删除文档
     * 流程：校验文档存在 -> 删除关联文件（可选） -> 删除数据库记录
     * 设计考虑：文件删除失败不影响数据库记录删除，保证数据一致性
     *
     * @param documentId 文档ID
     * @throws BizException 当文档不存在或数据库删除失败时抛出
     */
    @Override
    public void deleteDocument(String documentId) {
        // 1. 根据ID查询文档
        Document document = documentMapper.selectById(documentId);

        // 2. 检查文档是否存在
        if (document == null) {
            throw new BizException("文档不存在: " + documentId);
        }

        // 3. 尝试删除关联的物理文件
        try {
            DocumentDTO documentDTO = documentConverter.toDTO(document);
            // 检查文件路径是否存在
            if (documentDTO.getMetadata().getFilePath() != null && !documentDTO.getMetadata().getFilePath().isEmpty()) {
                String filePath = documentDTO.getMetadata().getFilePath();
                documentStorageService.deleteFile(filePath);
            }
        } catch (Exception e) {
            // 文件删除失败，记录警告日志但不中断流程
            // 设计考虑：文件删除失败不应影响数据库记录的删除
            log.warn("删除文件失败，继续删除文档记录: documentId={}, error={}", documentId, e.getMessage());
        }


        // 5. 删除数据库中的文档记录 （CASCADE会自动清理chunk_bge_m3表）
        int result = documentMapper.deleteById(documentId);
        if (result <= 0) {
            throw new BizException("删除文档失败");
        }
    }

    /**
     * 更新文档信息
     * 流程：查询文档 -> 转换为DTO -> 根据请求更新字段 -> 转换为实体 -> 更新数据库
     *
     * @param documentId 文档ID
     * @param request    更新文档请求对象
     * @throws BizException     当文档不存在或更新失败时抛出
     * @throws RuntimeException 当JSON处理失败时抛出
     */
    @Override
    public void updateDocument(String documentId, UpdateDocumentRequest request) {
        try {
            // 1. 根据ID查询现有文档
            Document existingDocument = documentMapper.selectById(documentId);

            // 2. 检查文档是否存在
            if (existingDocument == null) {
                throw new BizException("文档不存在: " + documentId);
            }

            // 3. 将实体转换为DTO
            DocumentDTO documentDTO = documentConverter.toDTO(existingDocument);

            // 4. 根据请求更新DTO字段
            documentConverter.updateDTOFromRequest(documentDTO, request);

            // 5. 将更新后的DTO转换为实体
            Document updateDocument = documentConverter.toEntity(documentDTO);

            // 6. 设置必要字段（保持ID、知识库ID和创建时间不变，更新时间设为当前）
            updateDocument.setId(documentId);
            updateDocument.setKbId(existingDocument.getKbId());
            updateDocument.setUpdatedAt(LocalDateTime.now());
            updateDocument.setCreatedAt(existingDocument.getCreatedAt());

            // 7. 更新数据库记录
            int result = documentMapper.updateById(updateDocument);
            if (result <= 0) {
                throw new BizException("更新文档失败");
            }
        } catch (JsonProcessingException e) {
            // JSON序列化/反序列化异常，包装为运行时异常抛出
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建文档（仅创建数据库记录，不涉及文件上传）
     *
     * @param request 创建文档请求对象
     * @return 创建文档响应，包含新创建文档的ID
     * @throws BizException     当数据库插入失败时抛出
     * @throws RuntimeException 当JSON处理失败时抛出
     */
    @Override
    public CreateDocumentResponse createDocument(CreateDocumentRequest request) {
        try {
            // 将请求对象转换为DTO
            DocumentDTO documentDTO = documentConverter.toDTO(request);
            // 将DTO转换为实体
            Document document = documentConverter.toEntity(documentDTO);

            // 设置创建时间和更新时间
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());

            // 插入数据库，ID由数据库自动生成
            int result = documentMapper.insert(document);
            if (result <= 0) {
                throw new BizException("创建文档失败");
            }

            // 返回创建结果
            return CreateDocumentResponse.builder().documentId(document.getId()).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理 Markdown 文件，解析章节并生成 chunks 用于 RAG 检索
     * 流程：读取文件 -> 解析 Markdown -> 遍历章节 -> 向量化标题 -> 存储 chunk
     *
     * @param kbId         知识库ID
     * @param documentId   文档ID
     * @param relativePath 文件相对路径
     */
    private void processMarkdownFile(String kbId, String documentId, String relativePath) {
        try {
            log.info("开始处理 Markdown 文档: kbId={}, documentId={}, filePath={}", kbId, documentId, relativePath);

            // 1. 根据相对路径获取完整文件路径
            Path filePath = documentStorageService.getFilePath(relativePath);

            // 2. 读取文件并解析 Markdown（使用 try-with-resources 自动关闭流）
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                List<MarkdownParserService.MarkdownSection> sections = markdownParserService.parseMarkdown(inputStream);

                // 临时调试输出（可移除）
                System.out.println(sections);

                // 3. 检查解析结果
                if (sections == null || sections.isEmpty()) {
                    log.warn("Markdown 文档解析后没有找到任何章节: documentId={}", documentId);
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                int chunkCount = 0;

                // 4. 遍历章节，为每个章节生成一个 chunk
                for (MarkdownParserService.MarkdownSection section : sections) {
                    String title = section.getTitle();
                    String content = section.getContent();

                    // 跳过空标题的章节
                    if (title == null || title.trim().isEmpty()) {
                        continue;
                    }

                    // 5. 使用 RAG 服务对标题进行向量化（生成 embedding）
                    float[] titleEmbedding = ragService.embed(title);

                    // 6. 构建 ChunkBgeM3 实体
                    ChunkBgeM3 chunkBgeM3 = ChunkBgeM3.builder().kbId(kbId)                      // 关联知识库
                            .docId(documentId)                // 关联文档
                            .content(content != null ? content : "")  // 章节内容
                            .metadata(null)                   // 元数据（可扩展存储标题等信息）
                            .embedding(titleEmbedding)        // 标题的向量表示
                            .createdAt(now)                   // 创建时间
                            .updatedAt(now)                   // 更新时间
                            .build();

                    // 7. 插入数据库
                    int result = chunkBgeM3Mapper.insert(chunkBgeM3);
                    if (result > 0) {
                        chunkCount++;
//                        redisVectorService.storeVector(chunkBgeM3.getId(), titleEmbedding, kbId);
                        log.debug("创建 chunk 成功: title={}, chunkId={}", title, chunkBgeM3.getId());
                    } else {
                        log.warn("创建 chunk 失败: title={}", title);
                    }
                }

                log.info("Markdown 文档处理完成: documentId={}, 共生成 {} 个 chunks", documentId, chunkCount);
            }
        } catch (Exception e) {
            // 捕获所有异常，记录日志但不抛出
            // 设计考虑：Markdown 处理失败不应影响文档上传的主流程
            log.error("处理 Markdown 文档失败: documentId={}", documentId, e);
        }
    }

    // 解析文件类型
    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}