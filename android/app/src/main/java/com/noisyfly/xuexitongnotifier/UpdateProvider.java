package com.noisyfly.xuexitongnotifier;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
public final class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"/update.apk".equals(uri.getPath()) || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"update.apk"),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) {
        if(!"/update.apk".equals(u.getPath())) return null;
        String[] columns=p==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:p; MatrixCursor result=new MatrixCursor(columns); Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++) { if(OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i]="update.apk"; else if(OpenableColumns.SIZE.equals(columns[i])) values[i]=new File(getContext().getCacheDir(),"update.apk").length(); }
        result.addRow(values); return result;
    }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
}
