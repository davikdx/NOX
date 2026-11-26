package br.app.nox;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only, single-file grants. Only generated PDFs immediately inside the report cache are addressable. */
public final class ReportProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri) {
        List<String> p=uri.getPathSegments();
        if(!getContext().getPackageName().concat(".reports").equals(uri.getAuthority()) || p.size()!=1 ||
            !p.get(0).matches("nox-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf"))
            throw new IllegalArgumentException("Relatório indisponível");
        return new File(new File(getContext().getCacheDir(),"reports"),p.get(0));
    }
    @Override public String getType(Uri uri){resolve(uri);return "application/pdf";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        File file=resolve(uri);
        String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor c=new MatrixCursor(cols);
        if(!file.isFile()) return c;
        Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++) {
            if(OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i]="Nox-Relatorio-do-sono.pdf";
            if(OpenableColumns.SIZE.equals(cols[i])) row[i]=file.length();
        }
        c.addRow(row);
        return c;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode)) throw new FileNotFoundException("Somente leitura");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
