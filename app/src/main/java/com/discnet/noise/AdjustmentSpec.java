package com.discnet.noise;

import java.util.Locale;

/** Single source of truth for storage, ranges, formatting and neutral values. */
final class AdjustmentSpec {
    static final String[] CATEGORIES={"Luz","Cor","Detalhes","Efeitos","Dither","ASCII","Ferramentas"};
    static final String[] NAMES={"Saturação","Vibração","Exposição","Contraste","Realces","Brancos","Pretos","Ruído","Matiz","Intensidade","Glow","Desvio RGB","Fade","Tons quentes","Poeira","Vinheta","Aberração cromática","Névoa","Nitidez","Realce de bordas","Níveis","Posição X","Posição Y","Tamanho do padrão","Densidade","Ondulação","Intensidade","Espaçamento","Espessura","Centro X","Centro Y","Desgaste","Intensidade","Frequência","Suavidade","Curvatura","Vinheta CRT","Tom do fósforo","Intensidade","Faixas","Deslocamento","Falhas","Estática","Monocromia","Densidade","Contraste","Brilho","Limiar","Espaçamento","Tamanho","Brilho","Sombras","Temperatura","Tonalidade","Granulação","Tamanho do grão","Aspereza","Limiar do glow","Raio do glow","Textura","HDR","Detalhes HDR","Redução de ruído","Preservar bordas","Opacidade da mesclagem"};
    static final int COUNT=NAMES.length;
    static final int[][] BASIC={{2,50,3,4,51,5,6,60,61,64},{0,1,52,53,8,13},{18,7,62,63,54,55,56,59,19,14}};
    static final String[] EFFECTS={"Vinheta","Fade","Glow","Cromática","CRT","Glitch","Anéis","Névoa"};
    static final int[][] EFFECT_PARAMS={{15},{12},{10,57,58},{16,11},{32,33,34,35,36,37},{38,39,40,41,42,43},{26,27,28,29,30,31},{17}};
    static boolean signed(int i){return i<9||i==50||i==51||i==52||i==53;}
    static boolean extended(int i){return i==9||i==10||(i>=12&&i<=15)||i==17||i==18||i==19||i==26||i==32||i==38||i==54||i==59;}
    static float min(int i){return signed(i)?-1:0;}
    static float max(int i){return extended(i)?2:1;}
    static float neutral(int i){
        if(i>=20&&i<26)return EditState.DITHER_DEFAULTS[i-20];
        if(i>=26&&i<44)return EditState.ART_DEFAULTS[i-26];
        if(i>=44&&i<50)return EditState.ASCII_DEFAULTS[i-44];
        if(i==55||i==56)return .4f;
        if(i==57)return .45f;
        if(i==58)return .15f;
        if(i==63)return .55f;
        if(i==64)return 1f;
        return 0;
    }
    static float clamp(int i,float n){return Float.isFinite(n)?Math.max(min(i),Math.min(max(i),n)):neutral(i);}
    static float display(int i,float n){
        if(i==2)return n*3;
        if(i==8)return n*180;
        if(i==44)return 24+86*n;
        if(i==20)return 3+8*n;
        if(i==55)return 1+7*n;
        if(i==58)return 1+23*n;
        return n*100;
    }
    static float fromDisplay(int i,float n){
        if(i==2)return clamp(i,n/3);
        if(i==8)return clamp(i,n/180);
        if(i==44)return clamp(i,(n-24)/86);
        if(i==20)return clamp(i,(n-3)/8);
        if(i==55)return clamp(i,(n-1)/7);
        if(i==58)return clamp(i,(n-1)/23);
        return clamp(i,n/100);
    }
    static String unit(int i){return i==2?" EV":i==8?"°":i==44?" col":i==55||i==58?" px":i==20?" níveis":"%";}
    static String format(int i,float n){return String.format(Locale.getDefault(),i==2?"%.2f%s":"%.0f%s",display(i,n),unit(i));}
    private AdjustmentSpec(){}
}
