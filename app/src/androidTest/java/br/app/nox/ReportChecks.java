package br.app.nox;
import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

final class ReportChecks {
    static void run(Context context) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i=0; i<20; i++) {
            long wake=now-i*86400000L;
            rows.add(J.obj("id","sample-"+i,"bed",wake-8*3600000L,"wake",wake,"up",wake+120000,
                "reportedLatency",15,"waso",10,"goal",420,"quality",4));
        }
        File file=File.createTempFile("nox-report",".pdf",context.getCacheDir());
        try {
            try(OutputStream out=new FileOutputStream(file)){
                SleepReport.write(new DiaryStats(rows,Collections.emptyList(),null,null),J.obj("goal",420),out);
            }
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(fd)){
                if(renderer.getPageCount()<2) throw new AssertionError("Relatório sem paginação");
                try(PdfRenderer.Page page=renderer.openPage(renderer.getPageCount()-1)){
                    if(page.getWidth()<=0)throw new AssertionError("Página inválida");
                }
            }
        } finally {file.delete();}
    }
}
