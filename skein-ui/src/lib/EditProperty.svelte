<script>
    import { createEventDispatcher, tick } from "svelte";
    import { Modal } from "flowbite-svelte";

    export let title;
    export let value;

    const dispatcher = createEventDispatcher();

    let running = false;
    let field;
    let editValue = null;

    export async function activate() {
        running = true;

        editValue = value;

        await tick();

        field.select();
    }

    function keydown(event) {
        if (event.code == "Escape") {
            running = false;
            event.preventDefault();
        }

        if (event.code == "Enter") {
            running = false;
            event.preventDefault();

            dispatcher("change", editValue);
        }
    }
</script>

<Modal {title} bind:open={running} size="sm" on:close={() => null}>
    <input
        type="text"
        bind:this={field}
        bind:value={editValue}
        on:keydown={keydown}
        class="text-sm w-full"
    />
</Modal>
