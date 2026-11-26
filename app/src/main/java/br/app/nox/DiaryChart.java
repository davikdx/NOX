package br.app.nox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class DiaryChart extends View {
    private final DiaryStats stats;
    private final int metric, ink;
    DiaryChart(Context context,DiaryStats stats,int metric,int ink) {
        super(context);
        this.stats=stats; this.metric=metric; this.ink=ink;
        setContentDescription(DiaryStats.TITLES[metric]+". "+stats.values(metric,-1).size()+" pontos disponíveis. Valor resumido: "+stats.aggregate(metric,-1)+". Valores por noite nos detalhes.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        canvas.save();
        canvas.scale(d,d);
        draw(canvas,0,0,getWidth()/d,getHeight()/d,stats,metric,ink,Color.luminance(ink)>0.55f?0xffb5afff:0xff6250be);
        canvas.restore();
    }
    static void draw(Canvas c,float x,float y,float width,float height,DiaryStats s,int metric,int ink,int accent) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink); p.setTextSize(11);
        List<double[]> points=new ArrayList<>();
        for(JSONObject n:s.nights) {
            Double val=s.metric(n,metric);
            if(val==null) continue;
            double v=(metric==2 && val<720)?val+1440:val;
            long day=DiaryStats.local(DiaryStats.anchor(n),n).toLocalDate().toEpochDay();
            points.add(new double[]{day,v});
        }
        if(points.isEmpty()) { c.drawText("Sem valores neste período",x+6,y+height/2,p); return; }
        double low=Double.POSITIVE_INFINITY, high=Double.NEGATIVE_INFINITY;
        for(double[] point:points){ low=Math.min(low,point[1]); high=Math.max(high,point[1]); }
        if(metric!=2 && metric!=3) low=0;
        if(high-low<0.0001) { high+=1; }
        float left=x+43,right=x+width-15,top=y+14,bottom=y+height-25;
        double first=points.get(0)[0],last=points.get(points.size()-1)[0];
        for(int step=0;step<3;step++){
            float yy=bottom-step*(bottom-top)/2;
            double v=low+(high-low)*step/2;
            p.setColor(ink);p.setAlpha(150);p.setTextAlign(Paint.Align.RIGHT);
            String tick=(metric==2||metric==3)?DiaryStats.clockMinute(v):DiaryStats.num(v);
            c.drawText(tick,left-5,yy+4,p);
            p.setAlpha(50);p.setStrokeWidth(0.75f);c.drawLine(left,yy,right,yy,p);
        }
        float prevX=0,prevY=0; double prevDay=-1;
        for(double[] pt:points) {
            float xx=first==last?(left+right)/2:left+(float)((pt[0]-first)/(last-first))*(right-left);
            float yy=bottom-(float)((pt[1]-low)/(high-low))*(bottom-top);
            p.setColor(accent);p.setStrokeWidth(2);
            if(prevDay>=0 && pt[0]-prevDay==1) c.drawLine(prevX,prevY,xx,yy,p);
            c.drawCircle(xx,yy,3.2f,p);
            prevX=xx;prevY=yy;prevDay=pt[0];
        }
        DateTimeFormatter axis=DateTimeFormatter.ofPattern("dd/MM/yy",DiaryStats.PT);
        p.setColor(ink);p.setTextAlign(Paint.Align.LEFT);
        c.drawText(LocalDate.ofEpochDay((long)first).format(axis),left,bottom+19,p);
        if(first!=last) {p.setTextAlign(Paint.Align.RIGHT);c.drawText(LocalDate.ofEpochDay((long)last).format(axis),right,bottom+19,p);}
    }
}
