package top.skyeyefast.mchjong.art;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Original botanical engravings, generated without platform fonts or runtime assets. */
final class FlowerArtwork {
    private static final int STEM = 0x286a43, LEAF = 0x3c8851, BRANCH = 0x755137;
    private FlowerArtwork() {}

    static void draw(Graphics2D graphics, int flower, int width, int height) {
        if (flower < 0 || flower >= 8) throw new IllegalArgumentException("Unknown flower");
        var g = (Graphics2D) graphics.create();
        try {
            g.scale(width / 256.0, height / 384.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            numeral(g, flower % 4 + 1, flower < 4 ? 0x22418a : 0xa62335);
            switch (flower) {
                case 0 -> plum(g);
                case 1 -> orchid(g);
                case 2 -> chrysanthemum(g);
                case 3 -> bamboo(g);
                case 4 -> spring(g);
                case 5 -> summer(g);
                case 6 -> autumn(g);
                case 7 -> winter(g);
                default -> throw new IllegalArgumentException("Unknown flower");
            }
        } finally { g.dispose(); }
    }

    private static void plum(Graphics2D g) {
        curve(g, BRANCH, 10, 84, 342, 107, 236, 185, 99);
        curve(g, BRANCH, 6, 120, 249, 90, 192, 49, 172);
        curve(g, BRANCH, 5, 151, 166, 173, 166, 216, 147);
        leaf(g, 108, 277, -50, 34, LEAF);
        blossom(g, 180, 111, 20, 0xc42f53, 0xf9ce67);
        blossom(g, 144, 183, 24, 0xd64361, 0xf9ce67);
        blossom(g, 65, 180, 18, 0xbb2446, 0xf9ce67);
        blossom(g, 206, 148, 13, 0xd85a70, 0xf9ce67);
    }

    private static void orchid(Graphics2D g) {
        curve(g, STEM, 5, 124, 338, 85, 232, 131, 125);
        curve(g, STEM, 4, 126, 338, 155, 207, 191, 184);
        for (int angle : new int[]{-54, -28, 20, 48}) leaf(g, 125, 340, angle, 141, LEAF);
        blossom(g, 126, 133, 23, 0x885aa2, 0xe7b657);
        blossom(g, 181, 191, 21, 0x9b68ad, 0xe7b657);
        blossom(g, 109, 208, 16, 0x6d488c, 0xf1d09c);
        oval(g, 98, 339, 61, 6, 0x8b9a70);
    }

    private static void chrysanthemum(Graphics2D g) {
        curve(g, STEM, 7, 111, 340, 155, 253, 146, 177);
        leaf(g, 119, 304, -54, 79, LEAF);
        leaf(g, 132, 280, 58, 72, STEM);
        leaf(g, 141, 245, -42, 57, LEAF);
        for (int petal = 0; petal < 18; petal++) {
            var p = (Graphics2D) g.create();
            p.translate(145, 168); p.rotate(petal * Math.PI / 9);
            oval(p, -8, -51, 16, 45, petal % 2 == 0 ? 0xd89319 : 0xe8ad2c);
            p.dispose();
        }
        for (int petal = 0; petal < 12; petal++) {
            var p = (Graphics2D) g.create();
            p.translate(145, 168); p.rotate(petal * Math.PI / 6);
            oval(p, -6, -29, 12, 30, 0xf0c45a);
            p.dispose();
        }
        oval(g, 135, 158, 20, 20, 0x9c651d);
    }

    private static void bamboo(Graphics2D g) {
        for (int stem = 0; stem < 3; stem++) {
            int x = 81 + stem * 48, top = 110 - stem * 17;
            curve(g, STEM, 15, x, 343, x - 5, 218, x + 9, top);
            curve(g, 0x65a064, 4, x - 3, 339, x - 8, 218, x + 5, top + 3);
            for (int y = top + 30; y < 330; y += 56) curve(g, 0x184c32, 5, x - 10, y, x + 10, y - 1);
        }
        for (int[] cluster : new int[][]{{93,181}, {143,235}, {185,142}, {128,307}})
            for (int angle : new int[]{-68,-34,43,71}) leaf(g, cluster[0], cluster[1], angle, 51, LEAF);
    }

    private static void spring(Graphics2D g) {
        curve(g, BRANCH, 7, 49, 319, 113, 269, 170, 157);
        curve(g, BRANCH, 4, 124, 237, 172, 233, 210, 205);
        for (int[] p : new int[][]{{161,176},{125,238},{201,208},{81,287}}) {
            leaf(g, p[0], p[1]+14, -60, 44, LEAF);
            blossom(g, p[0], p[1], 17, 0xda7894, 0xd9b844);
        }
        curve(g, 0x334252, 5, 53, 127, 79, 108, 101, 126);
        curve(g, 0x334252, 5, 101, 126, 123, 106, 144, 112);
        curve(g, 0x334252, 4, 101, 126, 100, 137);
    }

    private static void summer(Graphics2D g) {
        oval(g, 40, 309, 173, 12, 0xa1c9cc);
        oval(g, 69, 329, 123, 7, 0x7baeb8);
        curve(g, STEM, 7, 124, 316, 131, 230, 127, 182);
        leaf(g, 120, 305, -61, 91, LEAF);
        leaf(g, 136, 289, 63, 85, 0x548d4a);
        for (int angle : new int[]{-62, 62, -35, 35, 0}) {
            var p = (Graphics2D) g.create(); p.translate(128, 213); p.rotate(Math.toRadians(angle));
            var petal = new Path2D.Double(); petal.moveTo(0, 0);
            petal.curveTo(-34,-28,-20,-66,0,-88); petal.curveTo(21,-65,34,-26,0,0);
            p.setColor(new Color(angle == 0 ? 0xe9a4b9 : Math.abs(angle) == 35 ? 0xd96f91 : 0xba456c));
            p.fill(petal); p.dispose();
        }
        oval(g, 112, 202, 32, 10, 0xd8b33f);
    }

    private static void autumn(Graphics2D g) {
        curve(g, BRANCH, 7, 74, 339, 125, 266, 170, 167);
        for (int[] leaf : new int[][]{{157,168,-14,48}, {103,245,-68,38}, {177,269,41,35}}) {
            var p = (Graphics2D) g.create(); p.translate(leaf[0], leaf[1]); p.rotate(Math.toRadians(leaf[2]));
            p.scale(leaf[3]/48.0,leaf[3]/48.0);
            var shape = new Path2D.Double();
            int[][] outline={{0,17},{-40,-1},{-25,-13},{-47,-39},{-18,-31},{-16,-64},{0,-49},{18,-86},
                {30,-48},{47,-55},{41,-25},{65,-23},{43,-5},{43,13}};
            shape.moveTo(outline[0][0],outline[0][1]);
            for (int[] point : outline) shape.lineTo(point[0],point[1]);
            shape.closePath(); p.setColor(new Color(leaf[0] == 157 ? 0xbe4927 : 0xd98724)); p.fill(shape);
            curve(p, 0x8d422a, 3, 0, 26, 15, -30, 18, -68);
            curve(p, 0x8d422a, 2, 9, -7, -23, -28);
            curve(p, 0x8d422a, 2, 11, -13, 43, -29);
            p.dispose();
        }
    }

    private static void winter(Graphics2D g) {
        curve(g, BRANCH, 9, 73, 341, 119, 252, 196, 219);
        curve(g, BRANCH, 6, 127, 268, 100, 240, 68, 215);
        curve(g, 0xaac2ce, 7, 129, 262, 163, 229, 194, 217);
        curve(g, 0xaac2ce, 6, 123, 260, 93, 231, 69, 212);
        for (int[] flake : new int[][]{{102,116,31},{183,154,24},{61,171,15}}) {
            var p=(Graphics2D)g.create(); p.translate(flake[0],flake[1]);
            for (int arm=0;arm<6;arm++) {
                p.rotate(Math.PI/3);
                curve(p,0x46859f,4,0,0,0,-flake[2]);
                curve(p,0x46859f,3,-8,-flake[2]+5,0,-flake[2]+13,8,-flake[2]+5);
            }
            p.dispose();
        }
        oval(g, 140, 245, 11, 13, 0xb43d50);
    }

    private static void blossom(Graphics2D g, double x, double y, double radius, int color, int center) {
        for (int petal=0;petal<5;petal++) {
            double angle=petal*Math.PI*2/5-Math.PI/2;
            oval(g,x+Math.cos(angle)*radius*.56-radius*.55,y+Math.sin(angle)*radius*.56-radius*.55,radius*1.1,radius*1.1,color);
        }
        oval(g,x-radius*.22,y-radius*.22,radius*.44,radius*.44,center);
        for(int dot=0;dot<5;dot++) oval(g,x+Math.cos(dot*Math.PI*2/5)*radius*.34-1.5,
            y+Math.sin(dot*Math.PI*2/5)*radius*.34-1.5,3,3,center);
    }

    private static void leaf(Graphics2D g, double x, double y, double angle, double length, int color) {
        var p=(Graphics2D)g.create(); p.translate(x,y); p.rotate(Math.toRadians(angle));
        var leaf=new Path2D.Double(); leaf.moveTo(0,0);
        leaf.curveTo(-length*.27,-length*.44,-length*.12,-length*.79,0,-length);
        leaf.curveTo(length*.16,-length*.68,length*.24,-length*.23,0,0);
        p.setColor(new Color(color)); p.fill(leaf);
        curve(p,0x205f39,Math.max(1,length/40),0,-3,0,-length*.82); p.dispose();
    }

    private static void numeral(Graphics2D g, int number, int color) {
        switch(number) {
            case 1 -> curve(g,color,6,30,48,37,42,37,71);
            case 2 -> { curve(g,color,6,27,47,36,34,49,46); curve(g,color,6,49,46,43,57,28,70); curve(g,color,6,28,70,50,70); }
            case 3 -> { curve(g,color,6,27,43,52,36,42,54); curve(g,color,6,37,54,59,65,28,72); }
            case 4 -> { curve(g,color,6,44,43,28,62,52,62); curve(g,color,6,45,44,45,72); }
            default -> throw new IllegalArgumentException("Flower number");
        }
    }

    private static void curve(Graphics2D g,int color,double width,double... points) {
        var path=new Path2D.Double(); path.moveTo(points[0],points[1]);
        if(points.length==6) path.quadTo(points[2],points[3],points[4],points[5]);
        else for(int i=2;i<points.length;i+=2) path.lineTo(points[i],points[i+1]);
        g.setColor(new Color(color)); g.setStroke(new BasicStroke((float)width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); g.draw(path);
    }
    private static void oval(Graphics2D g,double x,double y,double width,double height,int color) {
        g.setColor(new Color(color)); g.fill(new Ellipse2D.Double(x,y,width,height));
    }
}
