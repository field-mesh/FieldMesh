package io.github.meshtactic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IconResIndex {

    public static class IconItem {
        private int iconResourceId;
        private int nameResourceId;

        public IconItem(int iconResourceId, int nameResourceId) {
            this.iconResourceId = iconResourceId;
            this.nameResourceId = nameResourceId;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        public int getNameResourceId() {
            return nameResourceId;
        }
    }

    public static List<Integer> iconList = Arrays.asList(
            R.drawable.circle,
            R.drawable.triangle,
            R.drawable.cross,
            R.drawable.square,
            R.drawable.pin,
            R.drawable.person1,
            R.drawable.medic,
            R.drawable.soldier,
            R.drawable.bycicle,
            R.drawable.motorcycle,
            R.drawable.car,
            R.drawable.truck,
            R.drawable.bus,
            R.drawable.quadcopter,
            R.drawable.uav,
            R.drawable.plane,
            R.drawable.helli,
            R.drawable.jets,
            R.drawable.small_boat,
            R.drawable.motorboat,
            R.drawable.ship,
            R.drawable.biohazard,
            R.drawable.exclamation,
            R.drawable.radioactive,
            R.drawable.lightvehicle,
            R.drawable.artillery,
            R.drawable.apc,
            R.drawable.aa,
            R.drawable.tank
    );

    public static List<Integer> iconNames = Arrays.asList(
            R.string.rNameCircle,
            R.string.rNameTriangle,
            R.string.rNameCross,
            R.string.rNameSquare,
            R.string.rNamePin,
            R.string.rNamePerson1,
            R.string.rNameMedic,
            R.string.rNameSoldier,
            R.string.rNameBycicle,
            R.string.rNameMotorcycle,
            R.string.rNameCar,
            R.string.rNameTruck,
            R.string.rNameBus,
            R.string.rNameQuadcopter,
            R.string.rNameUAV,
            R.string.rNamePlane,
            R.string.rNameHelli,
            R.string.rNameJest,
            R.string.rNameSmallBoat,
            R.string.rNameMotorBoat,
            R.string.rNameShip,
            R.string.rBiohazard,
            R.string.rExclamation,
            R.string.rRadioactive,
            R.string.rNameLightVehicle,
            R.string.rNameArtillery,
            R.string.rNameAPC,
            R.string.rNameAntiAir,
            R.string.rNameTank
    );

    public List<IconItem> getIconItemsList() {
        List<IconItem> items = new ArrayList<>();
        int size = Math.min(iconList.size(), iconNames.size());
        for (int i = 0; i < size; i++) {
            items.add(new IconItem(iconList.get(i), iconNames.get(i)));
        }
        return items;
    }

    public static int getIndexByIconResId(Integer iconResId) {
        if (iconResId == null) {
            return -1;
        }
        return iconList.indexOf(iconResId);
    }

    public static int getIconResIdbyIndex(int index) {
        if (index < 0 || index >= iconList.size()) {
            return -1;
        }
        return iconList.get(index);
    }

}
