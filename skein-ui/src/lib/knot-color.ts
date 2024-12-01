import { Category } from "./types";

export const category2color = new Map<Category, { border: string, background: string }>();

category2color[Category.ERROR] = {
    border: "border-rose-400",
    background: "bg-rose-200 hover:bg-rose-400",
};

category2color[Category.NEW] = {
    border: "border-yellow-200",
    background: "bg-yellow-200 hover:bg-yellow-300",
};

category2color[Category.OK] = { border: "border-slate-100", background: "hover:bg-slate-200" };

