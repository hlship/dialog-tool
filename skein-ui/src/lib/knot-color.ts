import { Category } from "./types";

type Category2Color =  Record<Category, { border: string, background: string }>;

export const category2color : Category2Color = {
    ok: { border: "border-slate-100", background: "hover:bg-slate-200" },
    new: {
        border: "border-yellow-200",
        background: "bg-yellow-200 hover:bg-yellow-300",
    },
    error: {
        border: "border-rose-400",
        background: "bg-rose-200 hover:bg-rose-400",
    }
};


