package com.sar.anaylze.api;

import com.sar.anaylze.dto.JdStructuredResponse;
import com.sar.anaylze.service.JdLlmService;
import com.sar.anaylze.service.PdfTextExtractorService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;

@Path("/api/jd")
@Produces(MediaType.APPLICATION_JSON)
public class JdParserResource {

    @Inject
    PdfTextExtractorService pdfTextExtractorService;

    @Inject
    JdLlmService jdLlmService;

    @POST
    @Path("/parse")
    @Blocking
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response parseJd(
            @RestForm("pdf") FileUpload pdf,
            @RestForm("requirement") String interviewRequirement
    ) {
        if (pdf == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("pdf file is required.")
                    .build();
        }

        try {
            String jdText = pdfTextExtractorService.extractText(pdf.uploadedFile());
            JdStructuredResponse response = jdLlmService.buildStructuredResponse(jdText, interviewRequirement);
            return Response.ok(response).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unable to read PDF file.")
                    .build();
        }
    }
}
