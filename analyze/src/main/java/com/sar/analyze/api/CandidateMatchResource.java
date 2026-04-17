package com.sar.anaylze.api;

import com.sar.anaylze.dto.CandidateJdMatchResponse;
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

    /**
     * Multipart form: {@code resume} = candidate CV PDF, {@code jd} = job description PDF.
     */
    @POST
    @Path("/score")
    @Blocking
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response scoreCandidate(
            @RestForm("resume") FileUpload resumePdf,
            @RestForm("jd") FileUpload jdPdf
    ) {
        if (resumePdf == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Field \"resume\" (PDF) is required.")
                    .build();
        }
        if (jdPdf == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Field \"jd\" (job description PDF) is required.")
                    .build();
        }

        try {
            String resumeText = pdfTextExtractorService.extractText(resumePdf.uploadedFile());
            String jdText = pdfTextExtractorService.extractText(jdPdf.uploadedFile());
            CandidateJdMatchResponse result = resumeJdMatchService.scoreResumeAgainstJd(resumeText, jdText);
            return Response.ok(result).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unable to read one or both PDF files.")
                    .build();
        }
    }
}
