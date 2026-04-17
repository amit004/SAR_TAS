package com.sar.anaylze.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sar.anaylze.dto.CandidateJdMatchResponse;
import com.sar.anaylze.dto.JdStructuredResponse;
import com.sar.anaylze.service.PdfTextExtractorService;
import com.sar.anaylze.service.ResumeJdMatchService;
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

@Path("/api/match")
@Produces(MediaType.APPLICATION_JSON)
public class CandidateMatchResource {

    @Inject
    PdfTextExtractorService pdfTextExtractorService;

    @Inject
    ResumeJdMatchService resumeJdMatchService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Multipart: {@code resume} = CV PDF (file); {@code jd_profile} = JSON string (same text as {@code POST /api/jd/parse} response body).
     */
    @POST
    @Path("/score")
    @Blocking
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response scoreCandidate(
            @RestForm("resume") FileUpload resumePdf,
            @RestForm("jd_profile") String jdProfileJson
    ) {
        if (resumePdf == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Field \"resume\" (PDF) is required.")
                    .build();
        }
        if (jdProfileJson == null || jdProfileJson.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Field \"jd_profile\" (JSON string from POST /api/jd/parse) is required.")
                    .build();
        }

        final JdStructuredResponse jdProfile;
        try {
            jdProfile = objectMapper.readValue(jdProfileJson.trim(), JdStructuredResponse.class);
        } catch (JsonProcessingException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("jd_profile must be valid JSON matching the /api/jd/parse response shape.")
                    .build();
        }

        try {
            String resumeText = pdfTextExtractorService.extractText(resumePdf.uploadedFile());
            CandidateJdMatchResponse result = resumeJdMatchService.scoreResumeAgainstStructuredJd(resumeText, jdProfile);
            return Response.ok(result).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unable to read resume PDF.")
                    .build();
        }
    }
}
